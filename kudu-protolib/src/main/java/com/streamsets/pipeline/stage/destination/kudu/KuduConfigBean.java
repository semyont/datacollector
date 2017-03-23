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
package com.streamsets.pipeline.stage.destination.kudu;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ListBeanModel;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.lib.el.RecordEL;
import com.streamsets.pipeline.lib.el.TimeEL;
import com.streamsets.pipeline.lib.el.TimeNowEL;
import com.streamsets.pipeline.lib.operation.UnsupportedOperationAction;
import com.streamsets.pipeline.lib.operation.UnsupportedOperationActionChooserValues;

import java.util.List;

public class KuduConfigBean {

  public static final String CONF_PREFIX = "kuduConfigBean.";

  // kudu tab
  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      label = "Kudu Masters",
      description = "Comma-separated list of \"host:port\" pairs of the masters",
      displayPosition = 10,
      group = "KUDU"
  )
  public String kuduMaster;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.STRING,
      elDefs = {RecordEL.class, TimeEL.class, TimeNowEL.class},
      evaluation = ConfigDef.Evaluation.EXPLICIT,
      defaultValue = "${record:attribute('tableName')}",
      label = "Table Name",
      description = "Kudu table to write to. If table doesn't exist, records will be treated as error records.",
      displayPosition = 20,
      group = "KUDU"
  )
  public String tableNameTemplate;

  @ConfigDef(required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "",
      label = "Field to Column Mapping",
      description = "Optionally specify additional field mappings when input field name and column name don't match.",
      displayPosition = 30,
      group = "KUDU"
  )
  @ListBeanModel
  public List<KuduFieldMappingConfig> fieldMappingConfigs;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "",
      label = "Default Operation",
      description = "Default operation to perform if sdc.operation.type is not set in record header.",
      displayPosition = 40,
      group = "KUDU"
  )
  @ValueChooserModel(KuduOperationChooserValues.class)
  public KuduOperationType defaultOperation;

  // advanced tab
  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue = "CLIENT_PROPAGATED",
      label = "External Consistency",
      description = "The external consistency mode",
      displayPosition = 10,
      group = "ADVANCED"
  )
  @ValueChooserModel(ConsistencyModeChooserValues.class)
  public ConsistencyMode consistencyMode;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.NUMBER,
      defaultValue = "10000",
      label = "Operation Timeout Milliseconds",
      description = "Sets the default timeout used for user operations (using sessions and scanners)",
      displayPosition = 20,
      group = "ADVANCED"
  )
  public int operationTimeout;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue= "DISCARD",
      label = "Unsupported Operation Handling",
      description = "Action to take when operation type is not supported",
      displayPosition = 30,
      group = "ADVANCED"
  )
  @ValueChooserModel(UnsupportedOperationActionChooserValues.class)
  public UnsupportedOperationAction unsupportedAction;
}
