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
package com.streamsets.datacollector.runner.production;

import com.streamsets.datacollector.io.DataStore;
import com.streamsets.datacollector.json.ObjectMapperFactory;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.restapi.bean.BeanHelper;
import com.streamsets.datacollector.restapi.bean.SourceOffsetJson;
import com.streamsets.datacollector.util.PipelineDirectoryUtil;
import com.streamsets.pipeline.api.impl.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

public class OffsetFileUtil {
  private static final Logger LOG = LoggerFactory.getLogger(ProductionSourceOffsetTracker.class);

  private static final String OFFSET_FILE = "offset.json";
  private static final Map<String, String> DEFAULT_OFFSET = Collections.emptyMap();
  private static final int MAX_RETRIES = 5;

  private OffsetFileUtil() {}

  public static File getPipelineOffsetFile(RuntimeInfo runtimeInfo, String pipelineName, String rev) {
    return new File(PipelineDirectoryUtil.getPipelineDir(runtimeInfo, pipelineName, rev), OFFSET_FILE);
  }

  public static Map<String, String> saveIfEmpty(RuntimeInfo runtimeInfo, String pipelineName, String rev) {
    File pipelineOffsetFile =  getPipelineOffsetFile(runtimeInfo, pipelineName, rev);
    SourceOffset sourceOffset;
    DataStore ds = new DataStore(pipelineOffsetFile);
    try {
      if (ds.exists()) {
        return readSourceOffsetFromDataStore(ds).getOffsets();
      } else {
        sourceOffset = new SourceOffset(SourceOffset.CURRENT_VERSION, DEFAULT_OFFSET);
        try (OutputStream os = ds.getOutputStream()) {
          ObjectMapperFactory.get().writeValue(os, BeanHelper.wrapSourceOffset(sourceOffset));
          ds.commit(os);
        } finally {
          ds.release();
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
    return sourceOffset.getOffsets();
  }

  public static void saveOffsets(RuntimeInfo runtimeInfo, String pipelineName, String rev, Map<String, String> offset) {
    LOG.debug("Saving offset {} for pipeline {}", offset, pipelineName);
    SourceOffset sourceOffset = new SourceOffset(SourceOffset.CURRENT_VERSION, offset);
    DataStore dataStore = new DataStore(OffsetFileUtil.getPipelineOffsetFile(runtimeInfo, pipelineName, rev));
    try (OutputStream os = dataStore.getOutputStream()) {
      ObjectMapperFactory.get().writeValue(os, BeanHelper.wrapSourceOffset(sourceOffset));
      dataStore.commit(os);
    } catch (IOException e) {
      LOG.error("Failed to save offset={}. Reason {}", sourceOffset.getOffsets(), e.toString(), e);
      throw new IllegalStateException(e);
    } finally {
      dataStore.release();
    }
  }

  public static void resetOffsets(RuntimeInfo runtimeInfo, String pipelineName, String rev) {
    saveOffsets(runtimeInfo, pipelineName, rev, DEFAULT_OFFSET);
  }

  public static Map<String, String> getOffsets(RuntimeInfo runtimeInfo, String pipelineName, String rev) {
    SourceOffset sourceOffset = getOffsetInternal(runtimeInfo, pipelineName, rev);
    return sourceOffset == null ? DEFAULT_OFFSET : sourceOffset.getOffsets();
  }

  private static SourceOffset getOffsetInternal(RuntimeInfo runtimeInfo, String pipelineName, String rev) {
    int retries = 0;
    while (retries < MAX_RETRIES) {
      try {
        File pipelineOffsetFile = getPipelineOffsetFile(runtimeInfo, pipelineName, rev);
        if (pipelineOffsetFile.exists()) {
          DataStore ds = new DataStore(pipelineOffsetFile);
          if (ds.exists()) {
            return readSourceOffsetFromDataStore(ds);
          }
        }

        return null;
      } catch (IOException e) {
        // this could fail if offset file is also being written to the same time
        LOG.warn(Utils.format("Retrieving offset failed with attempt {} due to {}", retries, e), e);
      }
      retries++;
    }
    throw new IllegalStateException(Utils.format("Retrieving offset failed for last attempt {}", retries));
  }

  private static SourceOffset readSourceOffsetFromDataStore(DataStore ds) throws IOException {
    try (InputStream is = ds.getInputStream()) {
      SourceOffsetJson sourceOffsetJson = ObjectMapperFactory.get().readValue(is, SourceOffsetJson.class);
      SourceOffset sourceOffset = BeanHelper.unwrapSourceOffset(sourceOffsetJson);
      SourceOffsetUpgrader.upgrade(sourceOffset);
      return sourceOffset;
    }
  }
}

