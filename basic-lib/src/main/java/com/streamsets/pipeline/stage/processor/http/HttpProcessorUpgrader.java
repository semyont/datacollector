/*
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
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.processor.http;

import com.google.common.base.Joiner;
import com.streamsets.pipeline.api.Config;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.StageUpgrader;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.upgrade.DataFormatUpgradeHelper;
import com.streamsets.pipeline.lib.http.JerseyClientUtil;

import java.util.ArrayList;
import java.util.List;

/** {@inheritDoc} */
public class HttpProcessorUpgrader implements StageUpgrader {
  private static final String CONF = "conf";
  private static final String CLIENT = "client";

  private final List<Config> configsToRemove = new ArrayList<>();
  private final List<Config> configsToAdd = new ArrayList<>();

  private static final Joiner joiner = Joiner.on(".");

  @Override
  public List<Config> upgrade(String library, String stageName, String stageInstance, int fromVersion, int toVersion, List<Config> configs) throws StageException {
    switch(fromVersion) {
      case 1:
        JerseyClientUtil.upgradeToJerseyConfigBean(configs);
        if (toVersion == 2) {
          break;
        }
        // fall through
      case 2:
        upgradeV2ToV3(configs);
        if (toVersion == 3) {
          break;
        }
        // fall through
      case 3:
        upgradeV3ToV4(configs);
        if (toVersion == 4) {
          break;
        }
      case 4:
        upgradeV4ToV5(configs);
        if (toVersion == 5) {
          break;
        }
      case 5:
        upgradeV5ToV6(configs);
        if (toVersion == 6) {
          break;
        }
      case 6:
        upgradeV6ToV7(configs);
        break;
      default:
        throw new IllegalStateException(Utils.format("Unexpected fromVersion {}", fromVersion));
    }
    return configs;
  }

  private void upgradeV2ToV3(List<Config> configs) {
    configs.add(new Config(joiner.join(CONF, "headerOutputLocation"), "HEADER"));
    // Default for upgrades is different so that we don't accidentally clobber possibly pre-existing attributes.
    configs.add(new Config(joiner.join(CONF, "headerAttributePrefix"), "http-"));
    configs.add(new Config(joiner.join(CONF, "headerOutputField"), ""));
  }

  private void upgradeV3ToV4(List<Config> configs) {
    configsToAdd.clear();
    configsToRemove.clear();

    for (Config config : configs) {
      if (joiner.join(CONF, CLIENT, "requestTimeoutMillis").equals(config.getName())) {
        configsToRemove.add(config);
        configsToAdd.add(new Config(joiner.join(CONF, CLIENT, "readTimeoutMillis"), config.getValue()));
      }
    }

    configsToAdd.add(new Config(joiner.join(CONF, CLIENT, "connectTimeoutMillis"), "0"));
    configsToAdd.add(new Config(joiner.join(CONF, "maxRequestCompletionSecs"), "60"));

    configs.removeAll(configsToRemove);
    configs.addAll(configsToAdd);
  }

  private void upgradeV4ToV5(List<Config> configs) {
    DataFormatUpgradeHelper.upgradeAvroParserWithSchemaRegistrySupport(configs);
  }

  private static void upgradeV5ToV6(List<Config> configs) {
    configs.add(new Config(joiner.join(CONF, CLIENT, "useOAuth2"), false));
  }

  private void upgradeV6ToV7(List<Config> configs) {
    configs.add(new Config(joiner.join(CONF, "rateLimit"), 0));
  }

}
