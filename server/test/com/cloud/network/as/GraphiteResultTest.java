// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.network.as;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GraphiteResultTest {

    @Test
    public void testGetAverage(){
        List<List<Number>> dataPoints = new ArrayList<>();
        dataPoints.add(Arrays.asList(new Number[]{20, 1426173040}));
        GraphiteResult graphiteResult = new GraphiteResult("stats.vm-1;.gauges.cpu", dataPoints);

        assert 20 == graphiteResult.getAverage();
    }

    @Test
    public void testGetAverageWithMoreThanOneDataPoint(){
        List<List<Number>> dataPoints = new ArrayList<>();
        dataPoints.add(Arrays.asList(new Number[]{10, 1426173040}));
        dataPoints.add(Arrays.asList(new Number[]{20, 1426173040}));
        dataPoints.add(Arrays.asList(new Number[]{30, 1426173040}));
        dataPoints.add(Arrays.asList(new Number[]{40, 1426173040}));
        GraphiteResult graphiteResult = new GraphiteResult("stats.vm-1;.gauges.cpu", dataPoints);

        assert 25 == graphiteResult.getAverage();
    }
}