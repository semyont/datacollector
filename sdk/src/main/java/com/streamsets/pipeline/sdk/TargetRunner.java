/**
 * Copyright 2015 StreamSets Inc.
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
package com.streamsets.pipeline.sdk;

import com.streamsets.datacollector.config.StageType;
import com.streamsets.datacollector.runner.BatchImpl;
import com.streamsets.pipeline.api.DeliveryGuarantee;
import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.impl.Utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TargetRunner extends StageRunner<Target> {
  private static final Logger LOG = LoggerFactory.getLogger(TargetRunner.class);

  @SuppressWarnings("unchecked")
  public TargetRunner(
    Class<Target> targetClass,
    Target target,
    Map<String, Object> configuration,
    boolean isPreview,
    OnRecordError onRecordError,
    Map<String, Object> constants,
    ExecutionMode executionMode,
    DeliveryGuarantee deliveryGuarantee,
    String resourcesDir
  ) {
    super(
      targetClass,
      target,
      StageType.TARGET,
      configuration,
      Collections.EMPTY_LIST,
      isPreview,
      onRecordError,
      constants,
      executionMode,
      deliveryGuarantee,
      resourcesDir
    );
  }

  @SuppressWarnings("unchecked")
  public TargetRunner(
    Class<Target> sourceClass,
    Map<String, Object> configuration,
    boolean isPreview,
    OnRecordError onRecordError,
    Map<String, Object> constants,
    ExecutionMode executionMode,
    DeliveryGuarantee deliveryGuarantee,
    String resourcesDir
  ) {
    super(
      sourceClass,
      StageType.TARGET,
      configuration,
      Collections.EMPTY_LIST,
      isPreview,
      onRecordError,
      constants,
      executionMode,
      deliveryGuarantee,
      resourcesDir
    );
  }

  public void runWrite(List<Record> inputRecords) throws StageException {
    LOG.debug("Stage '{}' write starts", getInfo().getInstanceName());
    ensureStatus(Status.INITIALIZED);
    BatchImpl batch = new BatchImpl(getInfo().getInstanceName(), "sdk", "sourceOffset", inputRecords);
    getStage().write(batch);
    LOG.debug("Stage '{}' write ends", getInfo().getInstanceName());
  }

  public static class Builder extends StageRunner.Builder<Target, TargetRunner, Builder> {

    public Builder(Class<? extends Target> targetClass, Target target) {
      super((Class<Target>)targetClass, target);
    }

    @SuppressWarnings("unchecked")
    public Builder(Class<? extends Target> targetClass) {
      super((Class<Target>) targetClass);
    }

    @Override
    public TargetRunner build() {
      Utils.checkState(outputLanes.isEmpty(), "A Target cannot have output streams");
      if(stage != null) {
        return new TargetRunner(
          stageClass,
          stage,
          configs,
          isPreview,
          onRecordError,
          constants,
          executionMode,
          deliveryGuarantee,
          resourcesDir
        );
      } else {
        return new TargetRunner(
          stageClass,
          configs,
          isPreview,
          onRecordError,
          constants,
          executionMode,
          deliveryGuarantee,
          resourcesDir
        );
      }
    }

  }

}