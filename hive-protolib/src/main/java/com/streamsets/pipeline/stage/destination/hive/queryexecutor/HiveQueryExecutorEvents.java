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
package com.streamsets.pipeline.stage.destination.hive.queryexecutor;

import com.streamsets.pipeline.lib.event.EventCreator;

public final class HiveQueryExecutorEvents {

  public static final String SUCCESSFUL_QUERY_EVENT = "successful-query";
  public static final String FAILED_QUERY_EVENT = "failed-query";

  public static final String QUERY_EVENT_FIELD = "query";
  public static final String UNEXECUTED_QUERIES_EVENT_FIELD = "unexecuted-queries";



  /**
   * Issued on every successful query execution
   */
  public static final  EventCreator successfulQuery = new EventCreator.Builder(SUCCESSFUL_QUERY_EVENT, 1)
    .withRequiredField(QUERY_EVENT_FIELD)
    .build();

  /**
   * Issued on every failed query execution
   * with an optional
   */
  public static final  EventCreator failedQuery = new EventCreator.Builder(FAILED_QUERY_EVENT, 1)
      .withRequiredField(QUERY_EVENT_FIELD)
      .withOptionalField(UNEXECUTED_QUERIES_EVENT_FIELD)
      .build();

}
