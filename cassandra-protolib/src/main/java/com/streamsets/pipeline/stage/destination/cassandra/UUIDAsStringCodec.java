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
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.stage.destination.cassandra;

import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.extras.codecs.MappingCodec;

import java.util.UUID;

public class UUIDAsStringCodec extends MappingCodec<String, UUID> {
  public UUIDAsStringCodec() {
    super(TypeCodec.uuid(), String.class);
  }

  @Override
  protected String deserialize(UUID uuid) { return uuid.toString(); }

  @Override
  protected UUID serialize(String o) { return UUID.fromString(o); }
}
