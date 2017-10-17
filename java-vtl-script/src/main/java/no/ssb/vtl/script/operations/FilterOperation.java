package no.ssb.vtl.script.operations;

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

import no.ssb.vtl.model.AbstractUnaryDatasetOperation;
import no.ssb.vtl.model.DataPoint;
import no.ssb.vtl.model.DataStructure;
import no.ssb.vtl.model.Dataset;
import no.ssb.vtl.model.VTLBoolean;
import no.ssb.vtl.model.VTLExpression2;
import no.ssb.vtl.model.VTLPredicate;
import no.ssb.vtl.script.operations.join.ComponentBindings;
import no.ssb.vtl.script.operations.join.DataPointBindings;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;

public class FilterOperation extends AbstractUnaryDatasetOperation {

    private final VTLExpression2 predicate;
    private final ComponentBindings componentBindings;

    @Deprecated
    public FilterOperation(Dataset dataset, VTLPredicate vtlPredicate) {
        super(checkNotNull(dataset, "the dataset was null"));
        checkNotNull(vtlPredicate, "the predicate was null");
        this.predicate = null;
        this.componentBindings = null;
    }

    public FilterOperation(Dataset dataset, VTLExpression2 predicate, ComponentBindings componentBindings) {
        super(checkNotNull(dataset, "the dataset was null"));
        this.predicate = checkNotNull(predicate);
        this.componentBindings = checkNotNull(componentBindings);
    }
    
    protected DataStructure computeDataStructure() {
        return getChild().getDataStructure();
    }


//    @Override
//    public String toString() {
//        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this);
//        Map<Boolean, List<DataPoint>> predicateResultMap = getChild().getData().collect(Collectors.partitioningBy(predicate));
//        helper.addValue(predicateResultMap);
//        helper.add("structure", getDataStructure());
//        return helper.omitNullValues().toString();
//    }

    @Override
    public Stream<DataPoint> getData() {
        DataPointBindings dataPointBindings = new DataPointBindings(componentBindings, getDataStructure());
        return getChild().getData()
                .peek(dataPoint -> dataPoint.add(null))
                .map(dataPointBindings::setDataPoint)
                .filter(bindings -> {
                    VTLBoolean resolved = (VTLBoolean) predicate.resolve(dataPointBindings);
                    return resolved.get();
                })
                .map(DataPointBindings::getDataPoint);
        //return getChild().getData().filter(predicate);
    }

    @Override
    public Optional<Map<String, Integer>> getDistinctValuesCount() {
        return Optional.empty();
    }

    @Override
    public Optional<Long> getSize() {
        return Optional.empty();
    }
}
