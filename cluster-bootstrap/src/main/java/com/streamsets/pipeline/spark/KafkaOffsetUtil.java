/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.spark;

import kafka.common.TopicAndPartition;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.streaming.kafka.HasOffsetRanges;
import org.apache.spark.streaming.kafka.OffsetRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class KafkaOffsetUtil {
  private static final Logger LOG = LoggerFactory.getLogger(KafkaOffsetUtil.class);
  private KafkaOffsetUtil() {}

  private static Map<Integer, Long> getOffsetToSave(OffsetRange[] offsetRanges) {
    Map<Integer, Long> partitionToOffset = new LinkedHashMap<>();
    for (int i = 0; i < offsetRanges.length; i++) {
      //Until offset is the offset till which the current SparkDriverFunction
      //Will read
      partitionToOffset.put(offsetRanges[i].partition(), offsetRanges[i].untilOffset());
      LOG.info(
          "Offset Range From RDD - Partition:{}, From Offset:{}, Until Offset:{}",
          offsetRanges[i].partition(),
          offsetRanges[i].fromOffset(),
          offsetRanges[i].untilOffset()
      );
    }
    return partitionToOffset;
  }

  public static Map<Integer, Long> getOffsets(JavaPairRDD<?, ?> byteArrayJavaRDD) {
    return getOffsetToSave(((HasOffsetRanges) (byteArrayJavaRDD.rdd())).offsetRanges());
  }

  @SuppressWarnings("unchecked")
  public static void saveOffsets(Map<Integer, Long> partitionOffset) {
    SparkStreamingBinding.offsetHelper.saveOffsets(partitionOffset);
  }

  public static Map<Integer, Long> readOffsets(int numberOfPartitions) {
    return SparkStreamingBinding.offsetHelper.readOffsets(numberOfPartitions);
  }

  public static Map<TopicAndPartition, Long> getOffsetForDStream(String topic, int numberOfPartitions) {
    Map<TopicAndPartition, Long> offsetForDStream = new HashMap<>();
    Map<Integer, Long> partitionsToOffset = readOffsets(numberOfPartitions);
    for (Map.Entry<Integer, Long> partitionAndOffset : partitionsToOffset.entrySet()) {
      offsetForDStream.put(new TopicAndPartition(topic, partitionAndOffset.getKey()), partitionAndOffset.getValue());
    }
    return offsetForDStream;
  }

}
