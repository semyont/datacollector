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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.mapr;

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.FieldSelectorModel;
import com.streamsets.pipeline.api.ValueChooserModel;
import com.streamsets.pipeline.lib.el.RecordEL;

public class MapRJsonConfigBean {

  @ConfigDef(required = true,
      type = ConfigDef.Type.STRING,
      defaultValue = "",
      label = "Table Name",
      description = "MapR DB JSON Destination Table",
      elDefs = {RecordEL.class},
      evaluation = ConfigDef.Evaluation.EXPLICIT,
      displayPosition = 10,
      group = "MAPR_JSON"
  )
  public String tableName;

  @ConfigDef(required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Create Table",
      description = "If checked, create the table if it does not exist.",
      displayPosition = 20,
      group = "MAPR_JSON"
  )
  public boolean createTable;


  @ConfigDef(required = true,
      type = ConfigDef.Type.MODEL,
      defaultValue="",
      label = "Row Key",
      description = "Select the field to use as the row key to select documents.  " +
          "Ensure this field is unique or documents may be skipped.",
      displayPosition = 30,
      group = "MAPR_JSON"
  )
  @FieldSelectorModel(singleValued = true)
  public String keyField;

  @ConfigDef(required = true,
      type = ConfigDef.Type.BOOLEAN,
      defaultValue = "false",
      label = "Process Row Key as Binary",
      description = "If checked, process the row key as binary, otherwise process it as String.",
      displayPosition = 40,
      group = "MAPR_JSON"
  )
  public boolean isBinaryRowKey;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.MODEL,
      defaultValue = "REPLACE",
      label = "Insert API",
      description = "Select which MapR DB API to use when inserting a record into "
          + "MapR JSON Document Database.  "
          + "When encountering a duplicate document _id Insert will fail.  "
          + "The record will be sent to the "
          + "On Record Error destination.  InsertOrReplace will replace the document when a duplicate "
          + "document _id is encountered.",
      displayPosition = 50,
      group = "MAPR_JSON"
  )
  @ValueChooserModel(InsertOrReplaceChooserValues.class)
  public InsertOrReplace insertOrReplace = InsertOrReplace.INSERT;

}
