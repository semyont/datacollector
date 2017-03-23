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
package com.streamsets.pipeline.lib.salesforce;

import com.streamsets.pipeline.api.Dependency;
import com.streamsets.pipeline.lib.el.VaultEL;
import com.streamsets.pipeline.api.ConfigDef;

public class ForceConfigBean {
  public static final String CONF_PREFIX = "forceConfig.";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Username",
      description = "Salesforce username, in the form user@example.com",
      displayPosition = 10,
      elDefs = VaultEL.class,
      group = "FORCE"
  )
  public String username;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Password",
      description = "Salesforce password, or an EL to load the password from a resource, for example, ${runtime:loadResource('forcePassword.txt',true)}",
      displayPosition = 20,
      elDefs = VaultEL.class,
      group = "FORCE"
  )
  public String password;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "login.salesforce.com",
      label = "Auth Endpoint",
      description = "Salesforce SOAP API Authentication Endpoint: login.salesforce.com for production/Developer Edition, test.salesforce.com for sandboxes",
      displayPosition = 30,
      group = "FORCE"
  )
  public String authEndpoint;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "38.0",
      label = "API Version",
      description = "Salesforce API Version",
      displayPosition = 40,
      group = "FORCE"
  )
  public String apiVersion;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      label = "Use Proxy",
      description = "Connect to Salesforce via a proxy server.",
      defaultValue = "false",
      displayPosition = 400,
      group = "ADVANCED"
  )
  public boolean useProxy = false;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      label = "Proxy Hostname",
      description = "Proxy Server Hostname",
      defaultValue = "",
      displayPosition = 410,
      group = "ADVANCED",
      dependsOn = "useProxy",
      triggeredByValue = "true"
  )
  public String proxyHostname = "";

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      label = "Proxy Port",
      description = "Proxy Server Port Number",
      defaultValue = "",
      displayPosition = 420,
      group = "ADVANCED",
      dependsOn = "useProxy",
      triggeredByValue = "true"
  )
  public int proxyPort = 0;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      label = "Proxy Requires Credentials",
      description = "Enable if you need to supply a username/password to connect via the proxy server.",
      defaultValue = "false",
      displayPosition = 430,
      group = "ADVANCED",
      dependsOn = "useProxy",
      triggeredByValue = "true"
  )
  public boolean useProxyCredentials = false;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Proxy Username",
      description = "Username for the proxy server.",
      displayPosition = 440,
      elDefs = VaultEL.class,
      group = "ADVANCED",
      dependencies = {
          @Dependency(configName = "useProxy", triggeredByValues = "true"),
          @Dependency(configName = "useProxyCredentials", triggeredByValues = "true")
      }
  )
  public String proxyUsername;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Proxy Password",
      description = "Password for the proxy server, or an EL to load the password from a resource, for example, ${runtime:loadResource('proxyPassword.txt',true)}",
      displayPosition = 450,
      elDefs = VaultEL.class,
      group = "ADVANCED",
      dependencies = {
          @Dependency(configName = "useProxy", triggeredByValues = "true"),
          @Dependency(configName = "useProxyCredentials", triggeredByValues = "true")
      }
  )
  public String proxyPassword;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Use Compression",
      displayPosition = 1000
  )
  public boolean useCompression;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Show Debug Trace",
      displayPosition = 1010
  )
  public boolean showTrace;
}
