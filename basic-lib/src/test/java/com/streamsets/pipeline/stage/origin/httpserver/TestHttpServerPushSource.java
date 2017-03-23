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
package com.streamsets.pipeline.stage.origin.httpserver;

import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.config.DataFormat;
import com.streamsets.pipeline.lib.http.HttpConstants;
import com.streamsets.pipeline.lib.httpsource.RawHttpConfigs;
import com.streamsets.pipeline.sdk.PushSourceRunner;
import com.streamsets.pipeline.sdk.StageRunner;
import com.streamsets.pipeline.stage.destination.sdcipc.Constants;
import com.streamsets.pipeline.stage.origin.lib.DataParserFormatConfig;
import com.streamsets.testing.NetworkUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TestHttpServerPushSource {

  @Test
  public void testSource() throws Exception {
    RawHttpConfigs httpConfigs = new RawHttpConfigs();
    httpConfigs.appId = "id";
    httpConfigs.port = NetworkUtils.getRandomPort();
    httpConfigs.maxConcurrentRequests = 1;
    httpConfigs.sslEnabled = false;
    HttpServerPushSource source =
        new HttpServerPushSource(httpConfigs, 1, DataFormat.TEXT, new DataParserFormatConfig());
    final PushSourceRunner runner =
        new PushSourceRunner.Builder(HttpServerPushSource.class, source).addOutputLane("a").build();
    runner.runInit();
    try {
      final List<Record> records = new ArrayList<>();
      runner.runProduce(Collections.<String, String>emptyMap(), 1, new PushSourceRunner.Callback() {
        @Override
        public void processBatch(StageRunner.Output output) {
          records.clear();
          records.addAll(output.getRecords().get("a"));
          runner.setStop();
        }
      });
      HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + httpConfigs.getPort())
          .openConnection();
      connection.setRequestMethod("POST");
      connection.setUseCaches(false);
      connection.setDoOutput(true);
      connection.setRequestProperty(Constants.X_SDC_APPLICATION_ID_HEADER, "id");
      connection.getOutputStream().write("Hello".getBytes());
      Assert.assertEquals(HttpURLConnection.HTTP_OK, connection.getResponseCode());
      runner.waitOnProduce();
      Assert.assertEquals(1, records.size());
      Assert.assertEquals("Hello", records.get(0).get("/text").getValue());


      // passing App Id via query param should fail when appIdViaQueryParamAllowed is false
      String url = "http://localhost:" + httpConfigs.getPort() +
          "?" + HttpConstants.SDC_APPLICATION_ID_QUERY_PARAM + "=id";
      Response response = ClientBuilder.newClient()
          .target(url)
          .request()
          .post(Entity.json("Hello"));
      Assert.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getStatus());

    } finally {
      runner.runDestroy();
    }
  }


  @Test
  public void testWithAppIdViaQueryParam() throws Exception {
    RawHttpConfigs httpConfigs = new RawHttpConfigs();
    httpConfigs.appId = "id";
    httpConfigs.port = NetworkUtils.getRandomPort();
    httpConfigs.maxConcurrentRequests = 1;
    httpConfigs.sslEnabled = false;
    httpConfigs.appIdViaQueryParamAllowed = true;
    HttpServerPushSource source =
        new HttpServerPushSource(httpConfigs, 1, DataFormat.TEXT, new DataParserFormatConfig());
    final PushSourceRunner runner =
        new PushSourceRunner.Builder(HttpServerPushSource.class, source).addOutputLane("a").build();
    runner.runInit();
    try {
      final List<Record> records = new ArrayList<>();
      runner.runProduce(Collections.<String, String>emptyMap(), 1, new PushSourceRunner.Callback() {
        @Override
        public void processBatch(StageRunner.Output output) {
          records.clear();
          records.addAll(output.getRecords().get("a"));
          runner.setStop();
        }
      });

      String url = "http://localhost:" + httpConfigs.getPort() +
          "?" + HttpConstants.SDC_APPLICATION_ID_QUERY_PARAM + "=id";
      Response response = ClientBuilder.newClient()
          .target(url)
          .request()
          .post(Entity.json("Hello"));
      Assert.assertEquals(HttpURLConnection.HTTP_OK, response.getStatus());
      runner.waitOnProduce();
      Assert.assertEquals(1, records.size());
      Assert.assertEquals("Hello", records.get(0).get("/text").getValue());

      // Passing wrong App ID in query param should return 403 response
      url = "http://localhost:" + httpConfigs.getPort() +
          "?" + HttpConstants.SDC_APPLICATION_ID_QUERY_PARAM + "=wrongid";
      response = ClientBuilder.newClient()
          .target(url)
          .request()
          .post(Entity.json("Hello"));
      Assert.assertEquals(HttpURLConnection.HTTP_FORBIDDEN, response.getStatus());


    } finally {
      runner.runDestroy();
    }
  }


}
