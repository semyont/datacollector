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
package com.streamsets.datacollector.client.cli.command.manager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.streamsets.datacollector.client.ApiClient;
import com.streamsets.datacollector.client.JSON;
import com.streamsets.datacollector.client.TypeRef;
import com.streamsets.datacollector.client.api.ManagerApi;
import com.streamsets.datacollector.client.cli.command.BaseCommand;
import io.airlift.airline.Command;
import io.airlift.airline.Option;

import java.util.Map;

@Command(name = "start", description = "Start Pipeline")
public class StartPipelineCommand extends BaseCommand {
  @Option(
    name = {"-n", "--name"},
    description = "Pipeline Name",
    required = true
  )
  public String pipelineName;

  @Option(
    name = {"-r", "--revision"},
    description = "Pipeline Revision",
    required = false
  )
  public String pipelineRev;

  @Option(
      name = {"-c", "--runtimeConstants"},
      description = "Runtime Constants",
      required = false
  )
  public String runtimeConstantsString;

  @Override
  public void run() {
    if(pipelineRev == null) {
      pipelineRev = "0";
    }
    Map<String, Object> runtimeConstants = null;
    try {
      ApiClient apiClient = getApiClient();
      ManagerApi managerApi = new ManagerApi(apiClient);
      if (runtimeConstantsString != null && runtimeConstantsString.trim().length() > 0) {
        JSON json = apiClient.getJson();
        TypeRef returnType = new TypeRef<Map<String, Object>>() {};
        runtimeConstants = json.deserialize(runtimeConstantsString, returnType);
      }
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
      System.out.println(mapper.writeValueAsString(
          managerApi.startPipeline(pipelineName, pipelineRev, runtimeConstants))
      );
    } catch (Exception ex) {
      if(printStackTrace) {
        ex.printStackTrace();
      } else {
        System.out.println(ex.getMessage());
      }
    }
  }
}
