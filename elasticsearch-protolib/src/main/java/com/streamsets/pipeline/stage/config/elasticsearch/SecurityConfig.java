/*
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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.config.elasticsearch;

import com.streamsets.pipeline.lib.el.VaultEL;
import com.streamsets.pipeline.api.ConfigDef;

public class SecurityConfig {

  public static final String CONF_PREFIX = "conf.securityConfig.";

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      defaultValue = "username:password",
      label = "Security Username/Password",
      description = "",
      dependsOn = "useSecurity^",
      triggeredByValue = "true",
      displayPosition = 10,
      elDefs = VaultEL.class,
      group = "SECURITY"
  )
  public String securityUser;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "SSL TrustStore Path",
      description = "",
      dependsOn = "useSecurity^",
      triggeredByValue = "true",
      displayPosition = 20,
      group = "SECURITY"
  )
  public String sslTrustStorePath;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "SSL TrustStore Password",
      description = "",
      dependsOn = "useSecurity^",
      triggeredByValue = "true",
      displayPosition = 30,
      elDefs = VaultEL.class,
      group = "SECURITY"
  )
  public String sslTrustStorePassword;
}
