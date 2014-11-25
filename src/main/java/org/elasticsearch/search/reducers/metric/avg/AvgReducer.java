/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.reducers.metric.avg;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.InternalAggregation;
import org.elasticsearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.elasticsearch.search.reducers.Reducer;
import org.elasticsearch.search.reducers.ReducerContext;
import org.elasticsearch.search.reducers.ReducerFactories;
import org.elasticsearch.search.reducers.ReducerFactory;
import org.elasticsearch.search.reducers.ReducerFactoryStreams;
import org.elasticsearch.search.reducers.ReductionExecutionException;

import java.io.IOException;

public class AvgReducer extends Reducer {

    public static final ReducerFactoryStreams.Stream STREAM = new ReducerFactoryStreams.Stream() {
        @Override
        public ReducerFactory readResult(StreamInput in) throws IOException {
            Factory factory = new Factory();
            factory.readFrom(in);
            return factory;
        }
    };
    protected String fieldName;
    private String bucketsPath;

    public static void registerStreams() {
        ReducerFactoryStreams.registerStream(STREAM, InternalAvg.TYPE.stream());
    }

    public AvgReducer(String name, String bucketsPath, String fieldName, ReducerFactories factories, ReducerContext context, Reducer parent) {
        super(name, factories, context, parent);
        this.bucketsPath = bucketsPath;
        this.fieldName = fieldName;
    }

    protected Object[] getProperties(Aggregation aggregation) {
        return (Object[]) aggregation.getProperty(fieldName);
    }

    @Override
    public InternalAggregation reduce(Aggregations aggregationsTree, Aggregation currentAggregation) {
        MultiBucketsAggregation aggregation;
        if (currentAggregation == null) {
            Object o = aggregationsTree.getProperty(bucketsPath);
            if (o instanceof MultiBucketsAggregation) {
               aggregation = (MultiBucketsAggregation) o;
            } else {
                throw new ReductionExecutionException("bucketsPath must resolve to a muti-bucket aggregation for reducer [" + name() + "]");
            }
        } else {
            if (currentAggregation instanceof MultiBucketsAggregation) {
                aggregation = (MultiBucketsAggregation) currentAggregation;
            } else {
                throw new ReductionExecutionException("aggregation must be a muti-bucket aggregation for reducer [" + name() + "]");
            }
        }
        return doReduce(aggregationsTree, aggregation);
    }

    public InternalAggregation doReduce(Aggregations aggregationsTree, Aggregation aggregation) throws ReductionExecutionException {
        Object[] bucketProperties = getProperties(aggregation);

        double avg = 0;
        for (Object bucketValue : bucketProperties) {
            avg += (double) bucketValue;
        }

        return new InternalAvg(name(), avg, bucketProperties.length);
    }

    public static class Factory extends ReducerFactory {

        private String bucketsPath;
        private String fieldName;

        public Factory() {
            super(InternalAvg.TYPE);
        }

        public Factory(String name, String bucketsPath, String fieldName) {
            super(name, InternalAvg.TYPE);
            this.fieldName = fieldName;
            this.bucketsPath = bucketsPath;
        }

        @Override
        public Reducer create(ReducerContext context, Reducer parent) {
            return new AvgReducer(name, bucketsPath, fieldName, factories, context, parent);
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            name = in.readString();
            bucketsPath = in.readString();
            String avgType = in.readString();

        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(name);
            out.writeString(bucketsPath);
        }
        
    }

}