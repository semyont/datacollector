/*
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
package com.streamsets.pipeline.stage.destination.elasticsearch;

import com.streamsets.pipeline.api.Config;
import com.streamsets.pipeline.api.StageUpgrader;
import com.streamsets.pipeline.stage.config.elasticsearch.ElasticsearchConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestElasticSearchDTargetUpgrader {

  @Test
  @SuppressWarnings("unchecked")
  public void testUpgrader() throws Exception {
    StageUpgrader upgrader = new ElasticsearchDTargetUpgrader();

    List<Config> configs = createConfigs();

    List<Config> newConfigs = upgrader.upgrade("l", "s", "i", 1, 6, configs);

    assertEquals(6, configs.size());
    assertEquals("elasticSearchConfigBean.timeDriver", newConfigs.get(0).getName());
    assertEquals("elasticSearchConfigBean.timeZoneID", newConfigs.get(1).getName());
    assertEquals("elasticSearchConfigBean.httpUris", newConfigs.get(2).getName());
    assertEquals("http://localhost:9300", ((List<String>)newConfigs.get(2).getValue()).get(0));
    assertEquals("elasticSearchConfigBean.useSecurity", newConfigs.get(3).getName());
    assertEquals("elasticSearchConfigBean.params", newConfigs.get(4).getName());
    assertEquals("elasticSearchConfigBean.defaultOperation", newConfigs.get(5).getName());
  }

  @Test
  public void testV6ToV7Upgrade() throws Exception {
    StageUpgrader upgrader = new ElasticsearchDTargetUpgrader();
    List<Config> configs = createConfigs();

    List<Config> newConfigs = upgrader.upgrade("l", "s", "i", 1, 7, configs);
    assertEquals(6, configs.size());
    newConfigs.forEach(config -> assertTrue(config.getName().startsWith(ElasticsearchConfig.CONF_PREFIX)));
  }

  private List<Config> createConfigs() {
    List<Config> configs = new ArrayList<>();
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "clusterName", "MyCluster"));
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "uris", Collections.EMPTY_LIST));
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "httpUri", "http://localhost:9300"));
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "useShield", false));
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "useFound", false));
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "configs", Collections.EMPTY_MAP));
    configs.add(new Config(ElasticsearchDTargetUpgrader.OLD_CONFIG_PREFIX + "upsert", false));

    return configs;
  }

}
