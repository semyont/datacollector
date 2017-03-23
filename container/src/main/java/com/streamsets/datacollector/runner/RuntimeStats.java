/**
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
package com.streamsets.datacollector.runner;

import com.fasterxml.jackson.core.JsonGenerator;
import com.streamsets.datacollector.http.GaugeValue;

import java.io.IOException;

public class RuntimeStats implements GaugeValue {

  private long batchCount;
  private String currentSourceOffset;
  private long currentBatchAge;
  private String currentStage;
  private long timeInCurrentStage;
  private long timeOfLastReceivedRecord;
  private long batchStartTime;
  private long lastBatchInputRecordsCount;
  private long lastBatchOutputRecordsCount;
  private long lastBatchErrorRecordsCount;
  private long lastBatchErrorMessagesCount;
  private long totalRunners;
  private long availableRunners;

  public RuntimeStats() {
    //initialize to current time, otherwise it will be 0 and will trigger the pipeline idle alert as soon as the
    // pipeline is started.
    timeOfLastReceivedRecord = System.currentTimeMillis();
  }

  public long getBatchCount() {
    return batchCount;
  }

  public void setBatchCount(long batchCount) {
    this.batchCount = batchCount;
  }

  public String getCurrentSourceOffset() {
    return currentSourceOffset;
  }

  public void setCurrentSourceOffset(String currentSourceOffset) {
    this.currentSourceOffset = currentSourceOffset;
  }

  public long getCurrentBatchAge() {
    return currentBatchAge;
  }

  public void setCurrentBatchAge(long currentBatchAge) {
    this.currentBatchAge = currentBatchAge;
  }

  public String getCurrentStage() {
    return currentStage;
  }

  public void setCurrentStage(String currentStage) {
    this.currentStage = currentStage;
  }

  public long getTimeInCurrentStage() {
    return timeInCurrentStage;
  }

  public void setTimeInCurrentStage(long timeInCurrentStage) {
    this.timeInCurrentStage = timeInCurrentStage;
  }

  public long getTimeOfLastReceivedRecord() {
    return timeOfLastReceivedRecord;
  }

  public void setTimeOfLastReceivedRecord(long timeOfLastReceivedRecord) {
    this.timeOfLastReceivedRecord = timeOfLastReceivedRecord;
  }

  public long getBatchStartTime() {
    return batchStartTime;
  }

  public void setBatchStartTime(long batchStartTime) {
    this.batchStartTime = batchStartTime;
  }

  public long getLastBatchInputRecordsCount() {
    return lastBatchInputRecordsCount;
  }

  public void setLastBatchInputRecordsCount(long lastBatchInputRecordsCount) {
    this.lastBatchInputRecordsCount = lastBatchInputRecordsCount;
  }

  public long getLastBatchErrorRecordsCount() {
    return lastBatchErrorRecordsCount;
  }

  public void setLastBatchErrorRecordsCount(long lastBatchErrorRecordsCount) {
    this.lastBatchErrorRecordsCount = lastBatchErrorRecordsCount;
  }

  public long getLastBatchOutputRecordsCount() {
    return lastBatchOutputRecordsCount;
  }

  public void setLastBatchOutputRecordsCount(long lastBatchOutputRecordsCount) {
    this.lastBatchOutputRecordsCount = lastBatchOutputRecordsCount;
  }

  public long getLastBatchErrorMessagesCount() {
    return lastBatchErrorMessagesCount;
  }

  public void setLastBatchErrorMessagesCount(long lastBatchErrorMessagesCount) {
    this.lastBatchErrorMessagesCount = lastBatchErrorMessagesCount;
  }

  public long getTotalRunners() {
    return totalRunners;
  }

  public void setTotalRunners(long totalRunners) {
    this.totalRunners = totalRunners;
  }

  public long getAvailableRunners() {
    return availableRunners;
  }

  public void setAvailableRunners(long availableRunners) {
    this.availableRunners = availableRunners;
  }

  @Override
  public void serialize(JsonGenerator jg) throws IOException {
    jg.writeStartObject();
    jg.writeObjectField("batchCount", batchCount);
    jg.writeObjectField("currentSourceOffset", currentSourceOffset);
    jg.writeObjectField("currentBatchAge", currentBatchAge);
    jg.writeObjectField("currentStage", currentStage);
    jg.writeObjectField("timeInCurrentStage", timeInCurrentStage);
    jg.writeObjectField("timeOfLastReceivedRecord", timeOfLastReceivedRecord);
    jg.writeObjectField("batchStartTime", batchStartTime);
    jg.writeObjectField("lastBatchInputRecordsCount", lastBatchInputRecordsCount);
    jg.writeObjectField("lastBatchOutputRecordsCount", lastBatchOutputRecordsCount);
    jg.writeObjectField("lastBatchErrorRecordsCount", lastBatchErrorRecordsCount);
    jg.writeObjectField("lastBatchErrorMessagesCount", lastBatchErrorMessagesCount);
    jg.writeObjectField("totalRunners", totalRunners);
    jg.writeObjectField("availableRunners", availableRunners);
    jg.writeEndObject();
  }

}
