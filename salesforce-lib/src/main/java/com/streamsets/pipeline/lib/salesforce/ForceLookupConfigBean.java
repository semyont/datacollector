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

import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ConfigDefBean;
import com.streamsets.pipeline.api.ListBeanModel;
import com.streamsets.pipeline.lib.el.RecordEL;
import com.streamsets.pipeline.lib.el.StringEL;
import com.streamsets.pipeline.stage.processor.kv.CacheConfig;

import java.util.List;

public class ForceLookupConfigBean extends ForceConfigBean {
  @ConfigDef(
      required = true,
      type = ConfigDef.Type.TEXT,
      mode = ConfigDef.Mode.SQL,
      defaultValue = "",
      label = "SOQL Query",
      description =
          "SELECT <field>, ... FROM <object name> WHERE <field> <operator> <expression>",
      elDefs = {StringEL.class, RecordEL.class},
      evaluation = ConfigDef.Evaluation.EXPLICIT,
      displayPosition = 50,
      group = "FORCE"
  )
  public String soqlQuery;

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.MODEL,
      label = "Field Mappings",
      defaultValue = "",
      description = "Mappings from Salesforce field names to SDC field names",
      displayPosition = 60,
      group = "FORCE"
  )
  @ListBeanModel
  public List<ForceSDCFieldMapping> fieldMappings;

  @ConfigDefBean(groups = "FORCE")
  public CacheConfig cacheConfig = new CacheConfig();

  @ConfigDef(
      required = true,
      type = ConfigDef.Type.BOOLEAN,
      label = "Create Salesforce Header Attributes",
      description = "Generates record header attributes that provide additional details about source data, such as the original data type or source object.",
      defaultValue = "true",
      displayPosition = 70,
      group = "ADVANCED"
  )
  public boolean createSalesforceNsHeaders = true;

  @ConfigDef(
      required = false,
      type = ConfigDef.Type.STRING,
      label = "Salesforce Header Prefix",
      description = "Prefix for the header attributes, used as follows: <prefix>.<field name>.<type of information>. For example: salesforce.<field name>.precision and salesforce.<field name>.scale",
      defaultValue = "salesforce.",
      displayPosition = 80,
      group = "ADVANCED",
      dependsOn = "createSalesforceNsHeaders",
      triggeredByValue = "true"
  )
  public String salesforceNsHeaderPrefix = "salesforce.";
}
