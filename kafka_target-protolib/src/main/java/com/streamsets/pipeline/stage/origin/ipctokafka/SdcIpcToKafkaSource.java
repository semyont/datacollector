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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.ipctokafka;

import com.streamsets.pipeline.lib.http.HttpConfigs;
import com.streamsets.pipeline.lib.sdcipc.SdcIpcRequestFragmenter;
import com.streamsets.pipeline.stage.destination.kafka.KafkaTargetConfig;
import com.streamsets.pipeline.stage.origin.tokafka.HttpServerToKafkaSource;

import java.util.List;

public class SdcIpcToKafkaSource extends HttpServerToKafkaSource {

  public static final String IPC_PATH = "/ipc/v1";

  public SdcIpcToKafkaSource(HttpConfigs httpConfigs, KafkaTargetConfig kafkaConfigs, int kafkaMaxMessageSizeKB) {
    super(IPC_PATH, httpConfigs, new SdcIpcRequestFragmenter(), kafkaConfigs, kafkaMaxMessageSizeKB);
  }

  @Override
  protected List<ConfigIssue> init() {
    List<ConfigIssue> issues = getHttpConfigs().init(getContext());
    issues.addAll(getFragmenter().init(getContext()));
    getKafkaConfigs().init(getContext(), issues);
    if (issues.isEmpty()) {
      issues.addAll(super.init());
    }
    return issues;
  }

  @Override
  public void destroy() {
    super.destroy();
    getFragmenter().destroy();
    getKafkaConfigs().destroy();
    getHttpConfigs().destroy();
  }

}
