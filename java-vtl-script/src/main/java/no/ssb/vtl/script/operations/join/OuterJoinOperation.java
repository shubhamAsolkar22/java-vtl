package no.ssb.vtl.script.operations.join;

/*-
 * ========================LICENSE_START=================================
 * Java VTL
 * %%
 * Copyright (C) 2016 - 2017 Hadrien Kohl
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
/*-
 * #%L
 * java-vtl-script
 * %%
 * Copyright (C) 2016 Hadrien Kohl
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.Order;
import no.ssb.vtl.model.VTLObject;
import no.ssb.vtl.script.support.Closer;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Outer join is a bit more complex than inner join since we need
 * to buffer all the rows that are not matching a match or one of
 * the input buffer goes past another one. This implementation does
 * not support the former yet.
 * <p>
 * Note for later, the "deduplicate" algorithm that pops up all around
 * the internet on selling paper websites probably works as below.
 * Could be worth a try to see if the code is clearer and/or more effective.
 * <p>
 * <pre><code>
 * r1 := [t1] {
 *   identifier table := "t1"
 * }
 * r2 := [t2] {
 *   identifier table := "t2"
 * }
 * r3 := [t3] {
 *   identifier table := "t3"
 * }
 *
 * rs := union(r1,r2,r3)
 *
 * // Cartesian product missing.
 * unfolded := [rs] {
 *   unfold table, x to "t1", "t2", "t3"
 *
 * }
 * </code></pre>
 */
public class OuterJoinOperation extends AbstractJoinOperation {

    public OuterJoinOperation(Map<String, Dataset> namedDatasets) {
        this(namedDatasets, Collections.emptySet());
    }

    public OuterJoinOperation(Map<String, Dataset> namedDatasets, Set<Component> identifiers) {
        super(namedDatasets, identifiers);
        // We need the identifiers in the case of inner join.
        ComponentBindings joinScope = this.getJoinScope();
        for (Component component : getCommonIdentifiers()) {
            joinScope.put(
                    getDataStructure().getName(component),
                    component
            );
        }
    }

    @Override
    protected BiFunction<DataPoint, DataPoint, DataPoint> getMerger(
            final Dataset leftDataset, final Dataset rightDataset
    ) {

        final Table<Component, Dataset, Component> componentMapping = getComponentMapping();
        final DataStructure structure = getDataStructure();
        final DataStructure rightStructure = rightDataset.getDataStructure();

        return (left, right) -> {

            /*
             * We overwrite the ids if right != null for simplicity.
             */
            DataPoint result;
            if (left != null) {
                result = DataPoint.create(left);
            } else {
                result = DataPoint.create(structure.size());
            }

            if (right != null) {
                Map<Component, VTLObject> leftMap = structure.asMap(result);
                Map<Component, VTLObject> rightMap = rightStructure.asMap(right);
                for (Map.Entry<Component, Component> mapping : componentMapping.column(rightDataset).entrySet()) {
                    Component to = mapping.getKey();
                    Component from = mapping.getValue();
                    leftMap.put(to, rightMap.get(from));
                }
            }

            return DataPoint.create(result);
        };
    }

    /**
     * Convert the {@link Order} so it uses the given structure.
     */
    private Order adjustOrderForStructure(Order orders, DataStructure dataStructure) {

        DataStructure structure = getDataStructure();
        Order.Builder adjustedOrders = Order.create(dataStructure);

        // Uses names since child structure can be different.
        for (Component component : orders.keySet()) {
            String name = structure.getName(component);
            if (dataStructure.containsKey(name))
                adjustedOrders.put(name, orders.get(component));
        }
        return adjustedOrders.build();
    }

    /**
     * TODO: Move to the {@link no.ssb.vtl.model.AbstractDatasetOperation}.
     */
    private Stream<DataPoint> getOrSortData(Dataset dataset, Order order, Dataset.Filtering filtering, Set<String> components) {
        Optional<Stream<DataPoint>> sortedData = dataset.getData(order, filtering, components);
        if (sortedData.isPresent()) {
            return sortedData.get();
        } else {
            return dataset.getData().sorted(order).filter(filtering);
        }
    }

    @Override
    public Optional<Stream<DataPoint>> getData(Order requestedOrder, Dataset.Filtering filtering, Set<String> components) {

        // Try to create a compatible order.
        // If not, the caller will have to sort the result manually.
        Optional<Order> compatibleOrder = createCompatibleOrder(getDataStructure(), getCommonIdentifiers(), requestedOrder);
        if (!compatibleOrder.isPresent()) {
            return Optional.empty();
        }

        Order requiredOrder = compatibleOrder.get();

        // Compute the predicate
        Order predicate = computePredicate(requiredOrder);

        Iterator<Dataset> iterator = datasets.values().iterator();
        Dataset left = iterator.next();
        Dataset right = left;

        // Close all children
        Closer closer = Closer.create();
        try {

            Table<Component, Dataset, Component> componentMapping = getComponentMapping();
            Stream<DataPoint> result = getOrSortData(
                    left,
                    adjustOrderForStructure(requiredOrder, left.getDataStructure()),
                    filtering,
                    components
            ).peek(new DataPointCapacityExpander(getDataStructure().size()));
            closer.register(result);


            boolean first = true;
            while (iterator.hasNext()) {
                left = right;
                right = iterator.next();

                Stream<DataPoint> rightStream = getOrSortData(
                        right,
                        adjustOrderForStructure(requiredOrder, right.getDataStructure()),
                        filtering,
                        components
                );
                closer.register(rightStream);

                // The first left stream uses its own structure. After that, the left data structure
                // will always be the resulting structure. We use a flag (first) to handle the first case
                // since the hotfix needs to quickly released but this code should be refactored.

                result = StreamSupport.stream(
                        new OuterJoinSpliterator<>(
                                new JoinKeyExtractor(
                                        first ? left.getDataStructure() : getDataStructure(),
                                        predicate,
                                        first ? componentMapping.column(left)::get : c -> c
                                ),
                                new JoinKeyExtractor(right.getDataStructure(), predicate, componentMapping.column(right)),
                                predicate,
                                getMerger(this, right),
                                result.spliterator(),
                                rightStream.spliterator()
                        ), false
                );

                first = false;
            }

            // Close all the underlying streams.
            return Optional.of(result.onClose(() -> {
                try {
                    closer.close();
                } catch (IOException e) {
                    // ignore (cannot happen).
                }
            }));

        } catch (Exception ex) {
            try {
                closer.close();
            } catch (IOException ioe) {
                ex.addSuppressed(ioe);
            }
            throw ex;
        }
    }

    /**
     * Compute the predicate.
     *
     * @param requestedOrder the requested order.
     * @return order of the common identifiers only.
     */
    private Order computePredicate(Order requestedOrder) {
        DataStructure structure = getDataStructure();

        // We need to create a fake structure to allow the returned
        // Order to work with the result of the key extractors.

        ImmutableSet<Component> commonIdentifiers = getCommonIdentifiers();
        DataStructure.Builder fakeStructure = DataStructure.builder();
        for (Component component : commonIdentifiers) {
            fakeStructure.put(structure.getName(component), component);
        }

        Order.Builder predicateBuilder = Order.create(fakeStructure.build());
        for (Component component : commonIdentifiers) {
            predicateBuilder.put(component, requestedOrder.getOrDefault(component, Order.Direction.ASC));
        }
        return predicateBuilder.build();
    }

    @Override
    public Optional<Map<String, Integer>> getDistinctValuesCount() {
        if (getChildren().size() == 1) {
            return getChildren().get(0).getDistinctValuesCount();
        } else {
            // TODO
            return Optional.empty();
        }
    }

    @Override
    public Optional<Long> getSize() {
        if (getChildren().size() == 1) {
            return getChildren().get(0).getSize();
        } else {
            // TODO
            return Optional.empty();
        }
    }
}
