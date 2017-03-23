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
package com.streamsets.datacollector.event.handler.remote;

import com.streamsets.datacollector.execution.PipelineState;
import com.streamsets.datacollector.execution.StateEventListener;
import com.streamsets.datacollector.util.Configuration;
import com.streamsets.datacollector.util.PipelineException;
import com.streamsets.dc.execution.manager.standalone.ThreadUsage;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.impl.Utils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class RemoteStateEventListener implements StateEventListener {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteStateEventListener.class);
  private static final String REMOTE_EVENTS_QUEUE_CAPACITY = "remote.events.queue.capacity";
  private static final int REMOTE_EVENTS_QUEUE_CAPACITY_DEFAULT = 1000;
  private final int capacity;
  private BlockingQueue<Pair<PipelineState, Map<String, String>>> pipelineStateQueue;

  @Inject
  public RemoteStateEventListener(Configuration conf) {
    capacity = conf.get(REMOTE_EVENTS_QUEUE_CAPACITY, REMOTE_EVENTS_QUEUE_CAPACITY_DEFAULT);
  }

  public void init() {
    pipelineStateQueue = new ArrayBlockingQueue<>(capacity);
  }

  @Override
  public void onStateChange(
      PipelineState fromState,
      PipelineState toState,
      String toStateJson,
      ThreadUsage threadUsage,
      Map<String, String> offset
  ) throws PipelineException {
    Object isRemote = toState.getAttributes().get(RemoteDataCollector.IS_REMOTE_PIPELINE);
    if ((isRemote == null) ? false : (boolean) isRemote) {
      if (pipelineStateQueue.offer(new ImmutablePair<>(toState, offset))) {
        LOG.debug(Utils.format("Adding status event for remote pipeline: '{}' in status: '{}'",
            toState.getName(),
            toState.getStatus()
        ));
      } else {
        LOG.warn(Utils.format("Cannot add status event for remote pipeline: '{}' in status: '{}'; Queue for " +
            "storing pipeline state events is full"), toState.getName(), toState.getStatus());
      }
    }
  }

  public Collection<Pair<PipelineState, Map<String, String>>> getPipelineStateEvents() {
    List<Pair<PipelineState, Map<String, String>>> pipelineStates = new ArrayList<>();
    pipelineStateQueue.drainTo(pipelineStates);
    // Keep last state for a given pipeline
    Map<String, Pair<PipelineState, Map<String, String>>> map = new HashMap<>();
    for (Pair<PipelineState, Map<String, String>> pipelineStateAndOffset: pipelineStates) {
      map.put(pipelineStateAndOffset.getLeft().getName(), pipelineStateAndOffset);
    }
    return map.values();
  }

  public void clear() {
    pipelineStateQueue.clear();
  }
}

