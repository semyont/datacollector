/**
 * Copyright 2017 StreamSets Inc.
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
package com.streamsets.pipeline.stage.origin.websocketserver;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.lib.el.VaultEL;
import com.streamsets.pipeline.lib.http.HttpConfigs;

public class WebSocketConfigs extends HttpConfigs {

  public WebSocketConfigs() {
    super(Groups.WEB_SOCKET.name(), "config.");
  }

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "8080",
      label = "WebSocket Listening Port",
      description = "WebSocket endpoint to listen for data.",
      displayPosition = 10,
      group = "WEB_SOCKET",
      min = 1,
      max = 65535
  )
  public int port;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "10",
      label = "Max Concurrent Requests",
      description = "Maximum number of concurrent requests allowed by the origin.",
      displayPosition = 15,
      group = "WEB_SOCKET",
      min = 1,
      max = 200
  )
  public int maxConcurrentRequests;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      label = "Application ID",
      description = "Only WebSocket requests presenting this token will be accepted.",
      displayPosition = 20,
      elDefs = VaultEL.class,
      group = "WEB_SOCKET"
  )
  public String appId;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Allow Application ID through Query Param",
      description = "Allow Application ID through Query Param - ws://localhost:8000?sdcApplicationId=<Application ID>",
      displayPosition = 40,
      group = "WEB_SOCKET"
  )
  public boolean appIdViaQueryParamAllowed;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      label = "Max Request Size (MB)",
      defaultValue = "100",
      displayPosition = 30,
      group = "WEB_SOCKET",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int maxRequestSizeMB;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      label = "Idle Timeout (ms)",
      defaultValue = "20000",
      displayPosition = 35,
      group = "WEB_SOCKET",
      min = 1,
      max = Integer.MAX_VALUE
  )
  public int idleTimeout;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Use WSS",
      description = "Encrypt WebSocket communication using TLS.",
      displayPosition = 40,
      group = "WEB_SOCKET"
  )
  public boolean sslEnabled;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Keystore File",
      description = "The keystore file is expected in the Data Collector resources directory",
      displayPosition = 50,
      group = "WEB_SOCKET",
      dependsOn = "sslEnabled",
      triggeredByValue = "true"
  )
  public String keyStoreFile;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Keystore Password",
      displayPosition = 60,
      elDefs = VaultEL.class,
      group = "WEB_SOCKET",
      dependsOn = "sslEnabled",
      triggeredByValue = "true"
  )
  public String keyStorePassword;

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public int getMaxConcurrentRequests() {
    return maxConcurrentRequests;
  }

  @Override
  public String getAppId() {
    return appId;
  }

  @Override
  public int getMaxHttpRequestSizeKB() {
    return maxRequestSizeMB;
  }

  @Override
  public boolean isSslEnabled() {
    return sslEnabled;
  }

  @Override
  public boolean isAppIdViaQueryParamAllowed() {
    return appIdViaQueryParamAllowed;
  }

  @Override
  public String getKeyStorePassword() {
    return keyStorePassword;
  }

  @Override
  public String getKeyStoreFile() {
    return keyStoreFile;
  }

  int getMaxRequestSizeMB() {
    return maxRequestSizeMB;
  }

  int getIdleTimeout() {
    return idleTimeout;
  }
}
