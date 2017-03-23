/**
 *
 *  Copyright 2017 StreamSets Inc.
 *
 *  Licensed under the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.streamsets.datacollector.pipeline.executor.spark.databricks;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.lib.el.VaultEL;

import java.util.List;


/**
 * This class is a bridge between this package and the actual SslConfigBean.
 * The actual config bean can be used for initializing etc, but it can't depend on anything from this stage.
 * So we have this one which can depend on other params from this stage, and the underlying config is used to
 * actual configure SSL using JerseyClientUtil
 */
public class SslConfigBean {

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Path to Trust Store",
      displayPosition = 1000,
      dependsOn = "clusterManager^^",
      triggeredByValue = "DATABRICKS",
      group = "SSL"
  )
  public String trustStorePath = "";

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Password",
      displayPosition = 1010,
      elDefs = VaultEL.class,
      dependsOn = "clusterManager^^",
      triggeredByValue = "DATABRICKS",
      group = "SSL"
  )
  public String trustStorePassword = ""; // NOSONAR

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Path to Key Store",
      displayPosition = 1020,
      dependsOn = "clusterManager^^",
      triggeredByValue = "DATABRICKS",
      group = "SSL"
  )
  public String keyStorePath = "";

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Password",
      displayPosition = 1030,
      elDefs = VaultEL.class,
      dependsOn = "clusterManager^^",
      triggeredByValue = "DATABRICKS",
      group = "SSL"
  )
  public String keyStorePassword = ""; // NOSONAR

  private com.streamsets.pipeline.lib.http.SslConfigBean underlyingConfig;

  /**
   * Validates the parameters for this config bean.
   * @param context Stage Context
   * @param prefix Prefix to the parameter names (e.g. parent beans)
   * @param issues List of issues to augment
   */
  public void init(Stage.Context context, String prefix, List<Stage.ConfigIssue> issues) {
    underlyingConfig = new com.streamsets.pipeline.lib.http.SslConfigBean();
    underlyingConfig.trustStorePassword = trustStorePassword;
    underlyingConfig.trustStorePath = trustStorePath;
    underlyingConfig.keyStorePassword = keyStorePassword;
    underlyingConfig.keyStorePath = keyStorePath;
    underlyingConfig.init(context, "SSL", prefix, issues);
  }

  public com.streamsets.pipeline.lib.http.SslConfigBean getUnderlyingConfig() {
    return underlyingConfig;
  }
}
