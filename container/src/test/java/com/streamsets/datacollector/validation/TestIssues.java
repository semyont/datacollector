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
package com.streamsets.datacollector.validation;

import com.google.common.collect.ImmutableList;
import com.streamsets.pipeline.api.ErrorCode;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestIssues {

  @Test
  public void testHasIssues() {
    Issues pipeline = new Issues(getPipelineIssues());
    assertTrue(pipeline.hasIssues());

    Issues stages = new Issues(getStagesIssues());
    assertTrue(stages.hasIssues());

    Issues issues = new Issues(getIssues());
    assertTrue(issues.hasIssues());
  }

  @Test
  public void testDeDuplication() {
    Issues issues = new Issues(getIssues());

    Map<String, List<Issue>> stageIssues = issues.getStageIssues();
    assertNotNull(stageIssues);
    assertEquals(2, stageIssues.size());
    assertTrue(stageIssues.containsKey("1"));
    assertTrue(stageIssues.containsKey("2"));

    // First stage
    assertEquals(2, stageIssues.get("1").size());

    // Second stage
    assertEquals(1, stageIssues.get("2").size());
  }

  @Test
  public void testGetIssueCount() {
    Issues pipeline = new Issues(getPipelineIssues());
    assertEquals(3, pipeline.getIssueCount());

    Issues stages = new Issues(getStagesIssues());
    assertEquals(3, stages.getIssueCount());

    Issues issues = new Issues(getIssues());
    assertEquals(6, issues.getIssueCount());
  }


  private List<Issue> getPipelineIssues() {
    return ImmutableList.of(
      new Issue(null, "a", "b", CustomErrors.ERR_001),
      new Issue(null, "a", "b", CustomErrors.ERR_002),
      new Issue(null, "a", "b", CustomErrors.ERR_001)
    );
  }

  private List<Issue> getStagesIssues() {
    return ImmutableList.of(
      new Issue("1", "a", "b", CustomErrors.ERR_001),
      new Issue("2", "a", "b", CustomErrors.ERR_002),
      new Issue("1", "a", "b", CustomErrors.ERR_001),
      new Issue("2", "a", "b", CustomErrors.ERR_002),
      new Issue("1", "a", "b", CustomErrors.ERR_003)
    );
  }

  private List<Issue> getIssues() {
    ImmutableList.Builder builder = ImmutableList.builder();
    builder.addAll(getPipelineIssues());
    builder.addAll(getStagesIssues());
    return builder.build();
  }

  private enum CustomErrors implements ErrorCode {
    ERR_001,
    ERR_002,
    ERR_003
    ;

    @Override
    public String getCode() {
      return name();
    }

    @Override
    public String getMessage() {
      return null;
    }
  }
}
