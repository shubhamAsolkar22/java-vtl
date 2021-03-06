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

import com.google.common.collect.ImmutableList;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.Filtering;
import no.ssb.vtl.model.FilteringSpecification;
import no.ssb.vtl.model.Ordering;
import no.ssb.vtl.model.OrderingSpecification;
import no.ssb.vtl.script.operations.VtlStream;
import no.ssb.vtl.script.support.Closer;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Outer join is a bit more complex than inner join since we need to buffer all the non matching rows
 * until a match occurs or one of the input buffer goes past another one.
 * This implementation does not support the later yet.
 * <p>
 * The "deduplicate" algorithm that pops up all around the internet on scientific paper selling websites
 * probably works as below. It could be worth a try to see if the code is clearer and/or more effective.
 * <p>
 * <pre><code>
 * // Mark each row.
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
        this(namedDatasets, Collections.emptyMap());
    }

    public OuterJoinOperation(Map<String, Dataset> namedDatasets, Map<String, Component> identifiers) {
        super(namedDatasets, identifiers);
    }

    @Override
    public Stream<DataPoint> computeData(Ordering orders, Filtering filtering, Set<String> components) {
        // Try to create a compatible order.
        Ordering requiredOrder = createCompatibleOrder(getDataStructure(), getCommonIdentifiers(), orders);

        // Compute the predicate
        Ordering predicate = computePredicate(requiredOrder);

        // TODO: Use abstract operation here.
        Iterator<Dataset> iterator = datasets.values().iterator();
        Dataset left = iterator.next();
        Dataset right = left;

        ImmutableList.Builder<Stream<DataPoint>> originals = ImmutableList.builder();

        // Close all children
        Closer closer = Closer.create();
        try {

            Stream<DataPoint> original = getOrSortData(
                    left,
                    adjustOrderForStructure(requiredOrder, left.getDataStructure()),
                    filtering,
                    components
            );
            originals.add(original);
            Stream<DataPoint> result = original.peek(new DataPointCapacityExpander(getDataStructure().size()));
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
                originals.add(rightStream);
                closer.register(rightStream);

                // The first left stream uses its own structure. After that, the left data structure
                // will always be the resulting structure. We use a flag (first) to handle the first case
                // since the hotfix needs to quickly released but this code should be refactored.

                result = StreamSupport.stream(
                        new OuterJoinSpliterator<>(
                                new JoinKeyExtractor(first ? left.getDataStructure() : getDataStructure(), predicate),
                                new JoinKeyExtractor(right.getDataStructure(), predicate), predicate,
                                new OuterJoinMerger(this, right),
                                result.spliterator(),
                                rightStream.spliterator()
                        ), false
                );

                first = false;
            }

            // Close all the underlying streams.
            Stream<DataPoint> delegate = result.onClose(() -> {
                try {
                    closer.close();
                } catch (IOException e) {
                    // ignore (cannot happen).
                }
            });

            return new VtlStream(
                    this,
                    delegate,
                    originals.build(),
                    orders,
                    filtering,
                    requiredOrder,
                    filtering
            );

        } catch (Exception ex) {
            try {
                closer.close();
            } catch (IOException ioe) {
                ex.addSuppressed(ioe);
            }
            throw ex;
        }
    }

    @Override
    public FilteringSpecification computeRequiredFiltering(FilteringSpecification filtering) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public OrderingSpecification computeRequiredOrdering(OrderingSpecification filtering) {
        throw new UnsupportedOperationException("TODO");
    }
}
