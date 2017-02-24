package no.ssb.vtl.script.operations;

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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import no.ssb.vtl.model.AbstractUnaryDatasetOperation;
import no.ssb.vtl.model.Component;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.VTLObject;

import java.util.*;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.*;

/**
 * Rename operation.
 * <p>
 * TODO: Implement {@link Dataset}
 */
public class RenameOperation extends AbstractUnaryDatasetOperation {

    private final Map<Component, Component> mapping = Maps.newHashMap();
    private final Map<Component, String> newNames;
    private final Map<Component, Component.Role> newRoles;

    public RenameOperation(Dataset dataset, Map<Component, String> newNames) {
        this(dataset, newNames, Collections.emptyMap());
    }

    public RenameOperation(Dataset dataset, Map<Component, String> newNames, Map<Component, Component.Role> newRoles) {
        super(checkNotNull(dataset));
        this.newNames = newNames;
        this.newRoles = newRoles;
    }

    /**
     * Compute a Map<String, String>
     */
    private static ImmutableMap<String, String> computeNames(Map<Component, String> newNames) {
        checkNotNull(newNames);
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Map.Entry<Component, String> entry : newNames.entrySet()) {
            builder.put(entry.getKey().getName(), entry.getValue());
        }
        return builder.build();
    }

    /**
     * Compute a Map<String, Role>
     */
    private static ImmutableMap<String, Component.Role> computeRoles(Map<Component, Component.Role> newRoles) {
        checkNotNull(newRoles);
        ImmutableMap.Builder<String, Component.Role> builder = ImmutableMap.builder();
        for (Map.Entry<Component, Component.Role> entry : newRoles.entrySet()) {
            builder.put(entry.getKey().getName(), entry.getValue());
        }
        return builder.build();
    }

    /**
     * Compute the role from the components.
     */
    private ImmutableMap<String, Component.Role> computeSameRole(Map<Component, String> newNames) {
        checkNotNull(newNames);
        ImmutableMap.Builder<String, Component.Role> builder = ImmutableMap.builder();
        for (Map.Entry<Component, String> entry : newNames.entrySet()) {
            builder.put(entry.getValue(), entry.getKey().getRole());
        }
        return builder.build();
    }

    @Override
    protected DataStructure computeDataStructure() {
        Map<Component, String> map = Maps.newHashMap();
        DataStructure.Builder newDataStructure = DataStructure.builder();
        for (Map.Entry<String, Component> componentEntry : getChild().getDataStructure().entrySet()) {
            Component component = componentEntry.getValue();
            if (newNames.containsKey(component)) {
                String oldName = component.getName();
                String newName = newNames.get(component);
                map.put(component, newName);
                newDataStructure.put(
                        newName,
                        newRoles.getOrDefault(component, component.getRole()),
                        component.getType()
                );
            } else {
                newDataStructure.put(componentEntry);
            }
        }
        DataStructure builtDataStructure = newDataStructure.build();

        // This is twisted, but there is no way to get the
        // component before the builder is built.
        for (Map.Entry<Component, String> entry : map.entrySet()) {
            mapping.put(entry.getKey(), builtDataStructure.get(entry.getValue()));
        }

        return builtDataStructure;
    }

    @Override
    @Deprecated
    public Stream<DataPoint> get() {
        return getData().map(o -> o);
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
        helper.addValue(newNames);
        helper.addValue(newRoles);
        helper.add("structure", getDataStructure());
        return helper.omitNullValues().toString();
    }

    @Override
    public Stream<? extends DataPoint> getData() {
        return getChild().get().map(dataPoint -> {
            LinkedList<Component> list = Lists.newLinkedList(getDataStructure().values());
            Map<VTLObject, Component> componentMap = getDataStructure().asInverseMap(dataPoint);
            dataPoint.replaceAll(vtlObject -> {
                Component component = componentMap.get(vtlObject);
                return  new VTLObject(component) {
                    @Override
                    public Object get() {
                        return vtlObject.get();
                    }
                };
            });
            return dataPoint;
        });
    }

    @Override
    public Optional<Map<String, Integer>> getDistinctValuesCount() {
        // TODO: Adjust the names.
        return getChild().getDistinctValuesCount();
    }

    @Override
    public Optional<Long> getSize() {
        return null;
    }
}
