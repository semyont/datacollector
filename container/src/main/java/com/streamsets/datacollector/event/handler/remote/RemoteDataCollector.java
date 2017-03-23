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
package com.streamsets.datacollector.event.handler.remote;

import com.google.common.annotations.VisibleForTesting;
import com.streamsets.datacollector.callback.CallbackInfo;
import com.streamsets.datacollector.callback.CallbackObjectType;
import com.streamsets.datacollector.config.PipelineConfiguration;
import com.streamsets.datacollector.config.RuleDefinitions;
import com.streamsets.datacollector.config.dto.ValidationStatus;
import com.streamsets.datacollector.event.dto.WorkerInfo;
import com.streamsets.datacollector.event.handler.DataCollector;
import com.streamsets.datacollector.execution.Manager;
import com.streamsets.datacollector.execution.PipelineState;
import com.streamsets.datacollector.execution.PipelineStateStore;
import com.streamsets.datacollector.execution.PipelineStatus;
import com.streamsets.datacollector.execution.PreviewOutput;
import com.streamsets.datacollector.execution.PreviewStatus;
import com.streamsets.datacollector.execution.Previewer;
import com.streamsets.datacollector.execution.Runner;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.runner.production.OffsetFileUtil;
import com.streamsets.datacollector.store.AclStoreTask;
import com.streamsets.datacollector.store.PipelineInfo;
import com.streamsets.datacollector.store.PipelineStoreTask;
import com.streamsets.datacollector.util.ContainerError;
import com.streamsets.datacollector.util.PipelineException;
import com.streamsets.datacollector.validation.Issues;
import com.streamsets.lib.security.acl.dto.Acl;
import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.impl.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RemoteDataCollector implements DataCollector {

  public static final String IS_REMOTE_PIPELINE = "IS_REMOTE_PIPELINE";
  private static final String NAME_AND_REV_SEPARATOR = "::";
  private static final Logger LOG = LoggerFactory.getLogger(RemoteDataCollector.class);
  private final Manager manager;
  private final PipelineStoreTask pipelineStore;
  private final List<String> validatorIdList;
  private final PipelineStateStore pipelineStateStore;
  private final RemoteStateEventListener stateEventListener;
  private final AclStoreTask aclStoreTask;
  private final AclCacheHelper aclCacheHelper;
  private final RuntimeInfo runtimeInfo;

  @Inject
  public RemoteDataCollector(
      Manager manager,
      PipelineStoreTask pipelineStore,
      PipelineStateStore pipelineStateStore,
      AclStoreTask aclStoreTask,
      RemoteStateEventListener stateEventListener,
      RuntimeInfo runtimeInfo,
      AclCacheHelper aclCacheHelper
  ) {
    this.manager = manager;
    this.pipelineStore = pipelineStore;
    this.pipelineStateStore = pipelineStateStore;
    this.validatorIdList = new ArrayList<>();
    this.stateEventListener = stateEventListener;
    this.runtimeInfo = runtimeInfo;
    this.aclStoreTask = aclStoreTask;
    this.aclCacheHelper = aclCacheHelper;
  }

  public void init() {
    stateEventListener.init();
    this.manager.addStateEventListener(stateEventListener);
    this.pipelineStore.registerStateListener(stateEventListener);
  }

  private void validateIfRemote(String name, String rev, String operation) throws PipelineException {
    if (!manager.isRemotePipeline(name, rev)) {
      throw new PipelineException(ContainerError.CONTAINER_01100, operation, name);
    }
  }

  @Override
  public void start(String user, String name, String rev) throws PipelineException, StageException {
    validateIfRemote(name, rev, "START");
    PipelineState pipelineState = pipelineStateStore.getState(name, rev);
    if (pipelineState.getStatus().isActive()) {
      LOG.warn("Pipeline {}:{} is already in active state {}", pipelineState.getName(), pipelineState.getRev(),
          pipelineState.getStatus());
    } else {
      manager.getRunner(user, name, rev).start();
    }
  }

  @Override
  public void stop(String user, String name, String rev) throws PipelineException {
    validateIfRemote(name, rev, "STOP");
    manager.getRunner(user, name, rev).stop();
  }

  @Override
  public void delete(String name, String rev) throws PipelineException {
    validateIfRemote(name, rev, "DELETE");
    pipelineStore.delete(name);
    pipelineStore.deleteRules(name);
  }

  @Override
  public void deleteHistory(String user, String name, String rev) throws PipelineException {
    validateIfRemote(name, rev, "DELETE_HISTORY");
    manager.getRunner(user, name, rev).deleteHistory();
  }

  @Override
  public void savePipeline(
      String user,
      String name,
      String rev,
      String description,
      String offset,
      PipelineConfiguration pipelineConfiguration,
      RuleDefinitions ruleDefinitions,
      Acl acl
  ) throws PipelineException {

    List<PipelineState> pipelineInfoList = manager.getPipelines();
    boolean pipelineExists = false;
    for (PipelineState pipelineState : pipelineInfoList) {
      if (pipelineState.getName().equals(name)) {
        pipelineExists = true;
        break;
      }
    }
    UUID uuid;
    if (!pipelineExists) {
      uuid = pipelineStore.create(user, name, name, description, true).getUuid();
    } else {
      validateIfRemote(name, rev, "SAVE");
      PipelineInfo pipelineInfo = pipelineStore.getInfo(name);
      uuid = pipelineInfo.getUuid();
      ruleDefinitions.setUuid(pipelineStore.retrieveRules(name, rev).getUuid());
    }
    pipelineConfiguration.setUuid(uuid);
    pipelineStore.save(user, name, rev, description, pipelineConfiguration);
    pipelineStore.storeRules(name, rev, ruleDefinitions);
    if (acl != null) { // can be null for old dpm or when DPM jobs have no acl
      aclStoreTask.saveAcl(name, acl);
    }
    LOG.info("Offset for remote pipeline '{}:{}' is {}", name, rev, offset);
    if (offset != null) {
      // TODO(SDC-4920): DPM Doesn't support two dimensional offset
      OffsetFileUtil.saveOffsets(runtimeInfo, name, rev, Collections.singletonMap(Source.POLL_SOURCE_OFFSET_KEY, offset));
    }
  }



  @Override
  public void savePipelineRules(String name, String rev, RuleDefinitions ruleDefinitions) throws PipelineException {
    validateIfRemote(name, rev, "SAVE_RULES");
    // Check for existence of pipeline first
    pipelineStore.getInfo(name);
    ruleDefinitions.setUuid(pipelineStore.retrieveRules(name, rev).getUuid());
    pipelineStore.storeRules(name, rev, ruleDefinitions);
  }

  @Override
  public void resetOffset(String user, String name, String rev) throws PipelineException {
    validateIfRemote(name, rev, "RESET_OFFSET");
    manager.getRunner(user, name, rev).resetOffset();
  }

  @Override
  public void validateConfigs(String user, String name, String rev) throws PipelineException {
    Previewer previewer = manager.createPreviewer(user, name, rev);
    validateIfRemote(name, rev, "VALIDATE_CONFIGS");
    previewer.validateConfigs(1000L);
    validatorIdList.add(previewer.getId());
  }

  @Override
  public void stopAndDelete(String user, String name, String rev) throws PipelineException, StageException {
    validateIfRemote(name, rev, "STOP_AND_DELETE");
    if (!pipelineStore.hasPipeline(name)) {
      LOG.warn("Pipeline {}:{} is already deleted", name, rev);
    } else {
      PipelineState pipelineState = pipelineStateStore.getState(name, rev);
      if (pipelineState.getStatus().isActive()) {
        manager.getRunner(user, name, rev).stop();
      }
      long now = System.currentTimeMillis();
      // wait for 10 secs for a graceful stop
      while (pipelineState.getStatus().isActive() && (System.currentTimeMillis() - now) < 10000) {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          throw new IllegalStateException("Interrupted while waiting for pipeline to stop " + e, e);
        }
        pipelineState = pipelineStateStore.getState(name, rev);
      }
      // If still active, force stop of this pipeline as we are deleting this anyways
      if (pipelineState.getStatus().isActive()) {
        pipelineStateStore.saveState(
            user,
            name,
            rev,
            PipelineStatus.STOPPED,
            "Stopping pipeline forcefully as we are performing a delete afterwards",
            pipelineState.getAttributes(),
            pipelineState.getExecutionMode(),
            pipelineState.getMetrics(),
            pipelineState.getRetryAttempt(),
            pipelineState.getNextRetryTimeStamp()
        );
      }
      delete(name, rev);
    }
  }

  // Returns info about remote pipelines that have changed since the last sending of events
  @Override
  public List<PipelineAndValidationStatus> getRemotePipelinesWithChanges() throws PipelineException {
    List<PipelineAndValidationStatus> pipelineAndValidationStatuses = new ArrayList<>();
    for (Pair<PipelineState, Map<String, String>> pipelineStateAndOffset: stateEventListener.getPipelineStateEvents()) {
      PipelineState pipelineState = pipelineStateAndOffset.getLeft();
      Map<String, String> offset = pipelineStateAndOffset.getRight();
      String name = pipelineState.getName();
      String rev = pipelineState.getRev();
      PipelineState latestState;
      boolean isClusterMode = (pipelineState.getExecutionMode() != ExecutionMode.STANDALONE) ? true : false;
      List<WorkerInfo> workerInfos = new ArrayList<>();
      String title;
      if (pipelineStore.hasPipeline(name)) {
        title = pipelineStore.getInfo(name).getTitle();
        Runner runner = manager.getRunner(pipelineState.getUser(), name, rev);
        latestState = runner.getState();
        if (isClusterMode) {
          workerInfos = getWorkers(runner.getSlaveCallbackList(CallbackObjectType.METRICS));
        }
      } else {
        title = null;
        latestState = pipelineState;
      }
      pipelineAndValidationStatuses.add(new PipelineAndValidationStatus(
          name,
          title,
          rev,
          true,
          latestState.getStatus(),
          latestState.getMessage(),
          workerInfos,
          isClusterMode,
          // TODO(SDC-4920): DPM Doesn't support two dimensional offset
          offset.get(Source.POLL_SOURCE_OFFSET_KEY),
          null
      ));
    }
    return pipelineAndValidationStatuses;
  }

  @Override
  public void syncAcl(Acl acl) throws PipelineException {
    if (acl == null) {
      return;
    }
    if (pipelineStore.hasPipeline(acl.getResourceId())) {
      validateIfRemote(acl.getResourceId(), "0", "SYNC_ACL");
      aclStoreTask.saveAcl(acl.getResourceId(), acl);
    } else {
      LOG.warn(ContainerError.CONTAINER_0200.getMessage(), acl.getResourceId());
    }
  }

  private List<WorkerInfo> getWorkers(Collection<CallbackInfo> callbackInfos) {
    List<WorkerInfo> workerInfos = new ArrayList<>();
    for (CallbackInfo callbackInfo : callbackInfos) {
      WorkerInfo workerInfo = new WorkerInfo();
      workerInfo.setWorkerURL(callbackInfo.getSdcURL());
      workerInfo.setWorkerId(callbackInfo.getSlaveSdcId());
      workerInfos.add(workerInfo);
    }
    return workerInfos;
  }

  private String getOffset(String pipelineName, String rev) {
    // TODO(SDC-4920): DPM Doesn't support two dimensional offset
    return OffsetFileUtil.getOffsets(runtimeInfo, pipelineName, rev).get(Source.POLL_SOURCE_OFFSET_KEY);
  }

  @Override
  public Collection<PipelineAndValidationStatus> getPipelines() throws IOException, PipelineException {
    List<PipelineState> pipelineStates = manager.getPipelines();
    //clear the queue as we will fetch all events
    stateEventListener.clear();
    Map<String, PipelineAndValidationStatus> pipelineStatusMap = new HashMap<>();
    Set<String> localPipelineIds = new HashSet<>();
    for (PipelineState pipelineState : pipelineStates) {
      boolean isRemote = false;
      String name = pipelineState.getName();
      PipelineInfo pipelineInfo = pipelineStore.getInfo(name);
      String title = pipelineInfo.getTitle();
      String rev = pipelineState.getRev();
      String user = pipelineState.getUser();
      if (manager.isRemotePipeline(name, rev)) {
        isRemote = true;
      }
      // ignore local and non active pipelines
      if (isRemote || manager.isPipelineActive(name, rev)) {
        List<WorkerInfo> workerInfos = new ArrayList<>();
        boolean isClusterMode = (pipelineState.getExecutionMode() != ExecutionMode.STANDALONE) ? true: false;
        if (isClusterMode) {
          for (CallbackInfo callbackInfo : manager.getRunner(user, name, rev).getSlaveCallbackList(CallbackObjectType.METRICS)) {
            WorkerInfo workerInfo = new WorkerInfo();
            workerInfo.setWorkerURL(callbackInfo.getSdcURL());
            workerInfo.setWorkerId(callbackInfo.getSlaveSdcId());
            workerInfos.add(workerInfo);
          }
        }
        Acl acl = null;
        if (!isRemote) { // if remote, dpm owns acl, sdc sends null acl
          localPipelineIds.add(name);
          acl = aclCacheHelper.getAcl(name);
        }
        pipelineStatusMap.put(getNameAndRevString(name, rev), new PipelineAndValidationStatus(
            name,
            title,
            rev,
            isRemote,
            pipelineState.getStatus(),
            pipelineState.getMessage(),
            workerInfos,
            isClusterMode,
            isRemote ? getOffset(name, rev) : null,
            acl
        ));
      }
    }
    aclCacheHelper.removeIfAbsent(localPipelineIds);
    setValidationStatus(pipelineStatusMap);
    return pipelineStatusMap.values();
  }

  private void setValidationStatus(Map<String, PipelineAndValidationStatus> pipelineStatusMap) {
    List<String> idsToRemove = new ArrayList<>();
    for (String previewerId : validatorIdList) {
      Previewer previewer = manager.getPreviewer(previewerId);
      if (previewer == null) {
        continue;
      }
      ValidationStatus validationStatus = null;
      Issues issues = null;
      String message = null;
      if (previewer != null) {
        PreviewStatus previewStatus = previewer.getStatus();
        switch (previewStatus) {
          case INVALID:
            validationStatus = ValidationStatus.INVALID;
            break;
          case TIMING_OUT:
          case TIMED_OUT:
            validationStatus = ValidationStatus.TIMED_OUT;
            break;
          case VALID:
            validationStatus = ValidationStatus.VALID;
            break;
          case VALIDATING:
            validationStatus = ValidationStatus.VALIDATING;
            break;
          case VALIDATION_ERROR:
            validationStatus = ValidationStatus.VALIDATION_ERROR;
            break;
          default:
            LOG.warn(Utils.format("Unrecognized validation state: '{}'", previewStatus));
        }
        if (!previewStatus.isActive()) {
          PreviewOutput previewOutput = previewer.getOutput();
          issues = previewOutput.getIssues();
          message = previewOutput.getMessage();
          idsToRemove.add(previewerId);
        }
      } else {
        LOG.warn(Utils.format("Previewer is null for id: '{}'", previewerId));
      }
      PipelineAndValidationStatus pipelineAndValidationStatus =
        pipelineStatusMap.get(getNameAndRevString(previewer.getName(), previewer.getRev()));
      if (pipelineAndValidationStatus == null) {
        LOG.warn("Preview pipeline: '{}'::'{}' doesn't exist", previewer.getName(), previewer.getRev());
      } else {
        pipelineAndValidationStatus.setValidationStatus(validationStatus);
        pipelineAndValidationStatus.setIssues(issues);
        pipelineAndValidationStatus.setMessage(message);
      }
    }
    for (String id : idsToRemove) {
      validatorIdList.remove(id);
    }
  }

  private String getNameAndRevString(String name, String rev) {
    return name + NAME_AND_REV_SEPARATOR + rev;
  }

  @VisibleForTesting
  List<String> getValidatorList() {
    return validatorIdList;
  }

}

