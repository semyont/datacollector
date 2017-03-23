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

package com.streamsets.pipeline.stage.destination.datalake.writer;

import com.google.common.annotations.VisibleForTesting;
import com.microsoft.azure.datalake.store.ADLStoreClient;
import com.microsoft.azure.datalake.store.oauth2.AzureADAuthenticator;
import com.microsoft.azure.datalake.store.oauth2.AzureADToken;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.base.OnRecordErrorException;
import com.streamsets.pipeline.api.el.ELEval;
import com.streamsets.pipeline.api.el.ELEvalException;
import com.streamsets.pipeline.api.el.ELVars;
import com.streamsets.pipeline.api.impl.Utils;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.config.WholeFileExistsAction;
import com.streamsets.pipeline.lib.el.RecordEL;
import com.streamsets.pipeline.lib.el.TimeEL;
import com.streamsets.pipeline.lib.el.TimeNowEL;
import com.streamsets.pipeline.lib.generator.DataGenerator;
import com.streamsets.pipeline.stage.destination.datalake.DataLakeTarget;
import com.streamsets.pipeline.stage.destination.lib.DataGeneratorFormatConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class RecordWriter {
  private final static Logger LOG = LoggerFactory.getLogger(RecordWriter.class);

  // FilePath with ADLS connections stream
  private Map<String, DataGenerator> generators;
  private final ADLStoreClient client;
  private final DataFormat dataFormat;
  private final DataGeneratorFormatConfig dataFormatConfig;
  private final String uniquePrefix;
  private final String fileNameSuffix;
  private final String fileNameEL;
  private final boolean dirPathTemplateInHeader;
  private final Target.Context context;

  private final ELEval dirPathTemplateEval;
  private final ELVars dirPathTemplateVars;
  private final ELEval fileNameEval;
  private final ELVars fileNameVars;
  private final boolean rollIfHeader;
  private final String rollHeaderName;
  private final long maxRecordsPerFile;
  private final WholeFileExistsAction wholeFileExistsAction;
  private final OutputStreamHelper outputStreamHelper;
  private final String authTokenEndpoint;
  private final String clientId;
  private final String clientKey;

  public RecordWriter(
      ADLStoreClient client,
      DataFormat dataFormat,
      DataGeneratorFormatConfig dataFormatConfig,
      String uniquePrefix,
      String fileNameSuffix,
      String fileNameEL,
      boolean dirPathTemplateInHeader,
      Target.Context context,
      boolean rollIfHeader,
      String rollHeaderName,
      long maxRecordsPerFile,
      WholeFileExistsAction wholeFileExistsAction,
      String authTokenEndpoint,
      String clientId,
      String clientKey
  ) {
    generators = new HashMap<>();
    dirPathTemplateEval = context.createELEval("dirPathTemplate");
    dirPathTemplateVars = context.createELVars();
    fileNameEval = context.createELEval("fileNameEL");
    fileNameVars = context.createELVars();

    this.client = client;
    this.dataFormat = dataFormat;
    this.dataFormatConfig = dataFormatConfig;
    this.uniquePrefix = uniquePrefix;
    this.fileNameSuffix = fileNameSuffix;
    this.fileNameEL = fileNameEL;
    this.dirPathTemplateInHeader = dirPathTemplateInHeader;
    this.context = context;
    this.rollIfHeader = rollIfHeader;
    this.rollHeaderName = rollHeaderName;
    this.maxRecordsPerFile = maxRecordsPerFile;
    this.wholeFileExistsAction = wholeFileExistsAction;
    this.outputStreamHelper = getOutputStreamHelper();

    this.authTokenEndpoint = authTokenEndpoint;
    this.clientId = clientId;
    this.clientKey = clientKey;
  }

  public void updateToken() throws IOException {
    AzureADToken token = AzureADAuthenticator.getTokenUsingClientCreds(authTokenEndpoint, clientId, clientKey);
    client.updateToken(token);
  }

  public void write(String filePath, Record record) throws StageException, IOException {
    DataGenerator generator = getGenerator(filePath);
    generator.write(record);

    if (dataFormat == DataFormat.WHOLE_FILE) {
      commitOldFile(filePath.substring(0, filePath.lastIndexOf("/")), filePath);
    }
  }

  /*
  return the full filePath for a record
   */
  public String getFilePath(
      String dirPathTemplate,
      Record record,
      Date recordTime
  ) throws ELEvalException {
    String dirPath;
    // get directory path
    if (dirPathTemplateInHeader) {
      dirPath = record.getHeader().getAttribute(DataLakeTarget.TARGET_DIRECTORY_HEADER);

      Utils.checkArgument(!(dirPath == null || dirPath.isEmpty()), "Directory Path cannot be null");
    } else {
      dirPath = resolvePath(dirPathTemplateEval, dirPathTemplateVars, dirPathTemplate, recordTime, record);
    }

    // SDC-5492: replace "//" to "/" in file path
    dirPath = dirPath.replaceAll("/+","/");
    if (dirPath.endsWith("/")) {
      dirPath = dirPath.substring(0, dirPath.length()-1);
    }

    return outputStreamHelper.getTempFilePath(dirPath, record, recordTime);
  }

  public void close() throws IOException {
    for (Map.Entry<String, DataGenerator> entry : generators.entrySet()) {
      entry.getValue().close();
      String dirPath = entry.getKey().substring(0, entry.getKey().lastIndexOf("/"));
      outputStreamHelper.commitFile(dirPath);
    }
    generators.clear();
    outputStreamHelper.clearStatus();
  }

  public void commitOldFile(String dirPath, String filePath) throws IOException {
    outputStreamHelper.commitFile(dirPath);
    generators.remove(filePath);
  }

  public static Date getRecordTime(
      ELEval elEvaluator,
      ELVars variables,
      String expression,
      Record record
  ) throws OnRecordErrorException {
    try {
      TimeNowEL.setTimeNowInContext(variables, new Date());
      RecordEL.setRecordInContext(variables, record);
      return elEvaluator.eval(variables, expression, Date.class);
    } catch (ELEvalException e) {
      LOG.error("Failed to evaluate expression '{}' : ", expression, e.toString(), e);
      throw new OnRecordErrorException(record, e.getErrorCode(), e.getParams());
    }
  }

  public void flush(String filePath) throws IOException {
    DataGenerator generator = generators.get(filePath);
    Utils.checkNotNull(generator, "File path does not exist: '" + filePath + "'");
    generator.flush();
  }

  private DataGenerator getGenerator(String filePath) throws StageException, IOException {
    DataGenerator generator = generators.get(filePath);
    if (generator == null) {
      generator = createDataGenerator(filePath);
      generators.put(filePath, generator);
    }
    return generator;
  }

  private DataGenerator createDataGenerator(String filePath) throws StageException, IOException {
    return dataFormatConfig.getDataGeneratorFactory().getGenerator(outputStreamHelper.getOutputStream(filePath));
  }

  OutputStreamHelper getOutputStreamHelper() {
    final String uniqueId = context.getSdcId() + "-" + context.getPipelineId() + "-" + context.getRunnerId();

    if (dataFormat != DataFormat.WHOLE_FILE) {
      return new DefaultOutputStreamHandler(
          client,
          uniquePrefix,
          fileNameSuffix,
          uniqueId,
          maxRecordsPerFile
      );
    } else {
      return new WholeFileFormatOutputStreamHandler(
          client,
          uniquePrefix,
          fileNameEL,
          fileNameEval,
          fileNameVars,
          wholeFileExistsAction
      );
    }
  }

  private String resolvePath(
      ELEval dirPathTemplateEval,
      ELVars dirPathTemplateVars,
      String dirPathTemplate,
      Date date,
      Record record
  ) throws ELEvalException {
    RecordEL.setRecordInContext(dirPathTemplateVars, record);
    if (date != null) {
      Calendar calendar = Calendar.getInstance();
      calendar.setTime(date);
      TimeEL.setCalendarInContext(dirPathTemplateVars, calendar);
    }
    return dirPathTemplateEval.eval(dirPathTemplateVars, dirPathTemplate, String.class);
  }

  @VisibleForTesting
  boolean shouldRoll(Record record, String dirPath) {
    if (rollIfHeader && record.getHeader().getAttribute(rollHeaderName) != null) {
      return true;
    }

    return outputStreamHelper.shouldRoll(dirPath);
  }
}
