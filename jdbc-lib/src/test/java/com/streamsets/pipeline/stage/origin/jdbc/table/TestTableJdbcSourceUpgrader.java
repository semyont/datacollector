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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.origin.jdbc.table;

import com.streamsets.pipeline.api.Config;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestTableJdbcSourceUpgrader {

  @Test
  public void testUpgradeV1ToV2() throws Exception {
    List<Config> configs = new ArrayList<>();
    TableJdbcSourceUpgrader upgrader = new TableJdbcSourceUpgrader();
    List<Config> upgradedConfigs =
        upgrader.upgrade("a", "b", "c", 1, 2, configs);
    Assert.assertEquals(1, upgradedConfigs.size());
    Config config = upgradedConfigs.get(0);
    Assert.assertEquals(
        TableJdbcConfigBean.TABLE_JDBC_CONFIG_BEAN_PREFIX + TableJdbcConfigBean.BATCHES_FROM_THE_RESULT_SET,
        config.getName()
    );
    Assert.assertEquals(
        -1,
        config.getValue()
    );
  }
}
