/**
 * Copyright 2016 StreamSets Inc.
 * <p>
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.mapr;
import com.streamsets.pipeline.api.ErrorCode;
import com.streamsets.pipeline.api.GenerateResourceBundle;

@GenerateResourceBundle
public enum Errors implements ErrorCode {
  MAPR_JSON_01("Table Name cannot be blank"),
  MAPR_JSON_02("Table '{}' does not exist or cannot access table"),
  MAPR_JSON_03("Error creating table '{}'"),
  MAPR_JSON_04("Exception while flushing '{}' "),
  MAPR_JSON_05("Exception while closing table '{}' "),
  MAPR_JSON_06("Exception while calling InsertOrReplace '{}' "),
  MAPR_JSON_07("Exception while Inserting record. '{}' "),
  MAPR_JSON_08("Field to use as _id column cannot be blank"),
  MAPR_JSON_09("Exception while creating, writing or closing JSON document: '{}'"),
  MAPR_JSON_10("Exception creating new MapRDB document. '{}'"),
  MAPR_JSON_11("Document key field '{}' does not exist in the record or is empty (or null)."),
  MAPR_JSON_12("Binary key error - invalid value or Field {} is type {} - not byte array. Use a FieldTypeConverter?"),
  MAPR_JSON_13("Exception converting key field '{}'"),
  MAPR_JSON_14("Conversion to byte array failed for Row Key - type '{}' "),
  MAPR_JSON_15("Field selected for record key '{}' does not exist."),
  MAPR_JSON_16("Error Validating EL '{}' in Table Name UI field. "),
  MAPR_JSON_18("EL Expression returned empty string."),
  ;

  private final String msg;

  Errors(String msg) {
    this.msg = msg;
  }

  @Override
  public String getCode() {
    return name();
  }

  @Override
  public String getMessage() {
    return msg;
  }
}
