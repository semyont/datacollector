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
package com.streamsets.pipeline.stage.origin.s3;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.collect.ImmutableSet;
import com.streamsets.pipeline.lib.hashing.HashingUtil;
import com.streamsets.pipeline.lib.io.fileref.AbstractFileRef;

import java.io.IOException;
import java.io.InputStream;

public final class S3FileRef extends AbstractFileRef {
  private final AmazonS3Client s3Client;
  private final S3ObjectSummary s3ObjectSummary;
  private final boolean useSSE;
  private final String customerKey;
  private final String customerKeyMd5;

  @SuppressWarnings("unchecked")
  public S3FileRef(
      AmazonS3Client s3Client,
      S3ObjectSummary s3ObjectSummary,
      boolean useSSE,
      String customerKey,
      String customerKeyMd5,
      int bufferSize,
      boolean createMetrics,
      long totalSizeInBytes,
      double rateLimit,
      boolean verifyChecksum,
      String checksum,
      HashingUtil.HashType checksumAlgorithm) {
    super(
        ImmutableSet.<Class<? extends AutoCloseable>>of(InputStream.class),
        bufferSize,
        createMetrics,
        totalSizeInBytes,
        rateLimit,
        verifyChecksum,
        checksum,
        checksumAlgorithm
    );
    this.s3Client = s3Client;
    this.s3ObjectSummary = s3ObjectSummary;
    this.useSSE = useSSE;
    this.customerKey = customerKey;
    this.customerKeyMd5 = customerKeyMd5;
  }
  @Override
  @SuppressWarnings("unchecked")
  public <T extends AutoCloseable> T createInputStream(Class<T> streamClassType) throws IOException {
    //The object is fetched every time a stream needs to be opened.
    return (T) AmazonS3Util.getObject(
        s3Client,
        s3ObjectSummary.getBucketName(),
        s3ObjectSummary.getKey(),
        useSSE,
        customerKey,
        customerKeyMd5
    ).getObjectContent();
  }


  @Override
  public String toString() {
    return "S3: Bucket='" + s3ObjectSummary.getBucketName()+ ", ObjectKey='" + s3ObjectSummary.getKey() + "'";
  }

  /**
   * Builder for building {@link S3FileRef}
   */
  public static final class Builder extends AbstractFileRef.Builder<S3FileRef, Builder> {
    private S3ObjectSummary s3ObjectSummary;
    private AmazonS3Client s3Client;
    private boolean useSSE;
    private String customerKey;
    private String customerKeyMd5;

    public Builder s3Client(AmazonS3Client s3Client) {
      this.s3Client = s3Client;
      return this;
    }

    public Builder s3ObjectSummary(S3ObjectSummary s3ObjectSummary) {
      this.s3ObjectSummary = s3ObjectSummary;
      return this;
    }

    public Builder useSSE(boolean useSSE) {
      this.useSSE = useSSE;
      return this;
    }

    public Builder customerKey(String customerKey) {
      this.customerKey = customerKey;
      return this;
    }

    public Builder customerKeyMd5(String customerKeyMd5) {
      this.customerKeyMd5 = customerKeyMd5;
      return this;
    }

    @Override
    public S3FileRef build() {
      return new S3FileRef(
          s3Client,
          s3ObjectSummary,
          useSSE,
          customerKey,
          customerKeyMd5,
          bufferSize,
          createMetrics,
          totalSizeInBytes,
          rateLimit,
          verifyChecksum,
          checksum,
          checksumAlgorithm
      );
    }
  }
}
