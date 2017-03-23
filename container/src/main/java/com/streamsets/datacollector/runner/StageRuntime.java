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

import com.google.common.base.Preconditions;
import com.streamsets.datacollector.config.StageConfiguration;
import com.streamsets.datacollector.config.StageDefinition;
import com.streamsets.datacollector.creation.PipelineBean;
import com.streamsets.datacollector.creation.StageBean;
import com.streamsets.datacollector.runner.production.ReportErrorDelegate;
import com.streamsets.datacollector.validation.Issue;
import com.streamsets.pipeline.api.Batch;
import com.streamsets.pipeline.api.BatchContext;
import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.OnRecordError;
import com.streamsets.pipeline.api.Processor;
import com.streamsets.pipeline.api.PushSource;
import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.Target;
import com.streamsets.pipeline.api.impl.CreateByRef;
import com.streamsets.pipeline.api.impl.Utils;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class StageRuntime implements PushSourceContextDelegate {
  private final PipelineBean pipelineBean;
  private final StageDefinition def;
  private final StageConfiguration conf;
  private final StageBean stageBean;
  private final Stage.Info info;
  private StageContext context;
  private volatile long runnerThread;

  /**
   * In case of PushSource, the delegate that needs to be called for it's callbacks.
   */
  private PushSourceContextDelegate pushSourceContextDelegate;

  /**
   * Optional error delegate.
   *
   * If not set, then the ErrorSink will be used instead.
   */
  private ReportErrorDelegate reportErrorDelegate;

  /**
   * Classloader of the main application persisted on each execute() and destroy() call.
   */
  private ClassLoader mainClassLoader;

  public StageRuntime(PipelineBean pipelineBean, final StageBean stageBean) {
    this.pipelineBean = pipelineBean;
    this.def = stageBean.getDefinition();
    this.stageBean = stageBean;
    this.conf = stageBean.getConfiguration();
    info = new Stage.Info() {
      @Override
      public String getName() {
        return def.getName();
      }

      @Override
      public int getVersion() {
        return def.getVersion();
      }

      @Override
      public String getInstanceName() {
        return conf.getInstanceName();
      }

      @Override
      public String toString() {
        return Utils.format("Info[instance='{}' name='{}' version='{}']", getInstanceName(), getName(), getVersion());
      }
    };

  }

  public Map<String, Object> getConstants() {
    return pipelineBean.getConfig().constants;
  }

  public StageDefinition getDefinition() {
    return def;
  }

  public StageConfiguration getConfiguration() {
    return conf;
  }

  public List<String> getRequiredFields() {
    return stageBean.getSystemConfigs().stageRequiredFields;
  }

  public List<String> getPreconditions() {
    return stageBean.getSystemConfigs().stageRecordPreconditions;
  }

  public OnRecordError getOnRecordError() {
    return stageBean.getSystemConfigs().stageOnRecordError;
  }

  public Stage getStage() {
    return stageBean.getStage();
  }

  public void setContext(StageContext context) {
    this.context = context;
  }

  public void setErrorAndEventSink(ErrorSink errorSink, EventSink eventSink) {
    context.setReportErrorDelegate(reportErrorDelegate == null ? errorSink : reportErrorDelegate);
    context.setErrorSink(errorSink);
    context.setEventSink(eventSink);
  }

  @SuppressWarnings("unchecked")
  public <T extends Stage.Context> T getContext() {
    return (T) context;
  }

  @SuppressWarnings("unchecked")
  public List<Issue> init() {
    Preconditions.checkState(context != null, "context has not been set");
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if(context.isPreview()) {
      runnerThread = Thread.currentThread().getId();
    }
    try {
      Thread.currentThread().setContextClassLoader(getDefinition().getStageClassLoader());
      List<Issue> issues = getStage().init(info, context);
      if (issues == null) {
        issues = Collections.emptyList();
      }
      return issues;
    } finally {
      Thread.currentThread().setContextClassLoader(cl);
    }
  }

  String execute(Callable<String> callable, ErrorSink errorSink, EventSink eventSink) throws StageException {
    mainClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      context.setPushSourceContextDelegate(this);
      setErrorAndEventSink(errorSink, eventSink);
      Thread.currentThread().setContextClassLoader(getDefinition().getStageClassLoader());

      try {
        // if the stage is annotated as recordsByRef it means it does not reuse the records/fields it creates, thus
        // we have to call it within a create-by-ref context so Field.create does not clone Fields and BatchMakerImpl
        // does not clone output records.
        return (def.getRecordsByRef() && !context.isPreview()) ? CreateByRef.call(callable) : callable.call();
      } catch (Exception ex) {
        if (ex instanceof StageException) {
          throw (StageException) ex;
        } else if (ex instanceof RuntimeException) {
          throw (RuntimeException) ex;
        } else {
          throw new RuntimeException(ex);
        }
      }

    } finally {
      setErrorAndEventSink(null, null);
      Thread.currentThread().setContextClassLoader(mainClassLoader);
    }
  }

  public void execute(final Map<String, String> offsets, final int batchSize) throws StageException {
      Callable<String> callable = new Callable<String>() {
        @Override
        public String call() throws Exception {
          switch (getDefinition().getType()) {
            case SOURCE: {
              if(getStage() instanceof PushSource) {
                ((PushSource)getStage()).produce(offsets, batchSize);
                return null;
              }
            }
            default: {
              throw new IllegalStateException(Utils.format("Unknown stage type: '{}'", getDefinition().getType()));
            }
          }
        }
      };

      execute(callable, null, null);
  }

  public String execute(
    final String previousOffset,
    final int batchSize,
    final Batch batch,
    final BatchMaker batchMaker,
    ErrorSink errorSink,
    EventSink eventSink
  ) throws StageException {
    Callable<String> callable = new Callable<String>() {
      @Override
      public String call() throws Exception {
        String newOffset = null;
        switch (getDefinition().getType()) {
          case SOURCE: {
            newOffset = ((Source) getStage()).produce(previousOffset, batchSize, batchMaker);
            break;
          }
          case PROCESSOR: {
            ((Processor) getStage()).process(batch, batchMaker);
            break;

          }
          case EXECUTOR:
          case TARGET: {
            ((Target) getStage()).write(batch);
            break;
          }
          default: {
            throw new IllegalStateException(Utils.format("Unknown stage type: '{}'", getDefinition().getType()));
          }
        }
        return newOffset;
      }
    };

    return execute(callable, errorSink, eventSink);
  }

  public void destroy(ErrorSink errorSink, EventSink eventSink) {
    mainClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      setErrorAndEventSink(errorSink, eventSink);
      Thread.currentThread().setContextClassLoader(getDefinition().getStageClassLoader());
      getStage().destroy();
    } finally {
      // Do not eventSink and errorSink to null when in preview mode AND current thread
      // is different from the one executing stages because stages might send error to errorSink.
      if (!context.isPreview() || runnerThread == (Thread.currentThread().getId())) {
        setErrorAndEventSink(null, null);
      }
      //we release the stage classloader back to the library  ro reuse (as some stages my have private classloaders)
      stageBean.releaseClassLoader();
      Thread.currentThread().setContextClassLoader(mainClassLoader);
    }
  }

  public Stage.Info getInfo() {
    return info;
  }

  /**
   * For all PushSource callbacks we have to make sure that we get back to a security context
   * of SDC container module, otherwise we won't be able to update state files with new offsets
   * and other stuff.
   */

  @Override
  public BatchContext startBatch() {
    return (BatchContext) AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        try {
          Thread.currentThread().setContextClassLoader(mainClassLoader);
          return pushSourceContextDelegate.startBatch();
        } finally {
          Thread.currentThread().setContextClassLoader(getDefinition().getStageClassLoader());
        }
      }
    });
  }

  @Override
  public boolean processBatch(final BatchContext batchContext, final String entity, final String offset) {
    return (boolean) AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        try {
          Thread.currentThread().setContextClassLoader(mainClassLoader);
          return pushSourceContextDelegate.processBatch(batchContext, entity, offset);
        } finally {
          Thread.currentThread().setContextClassLoader(getDefinition().getStageClassLoader());
        }
      }
    });
  }

  @Override
  public void commitOffset(final String entity, final String offset) {
    AccessController.doPrivileged(new PrivilegedAction() {
      public Object run() {
        try {
          Thread.currentThread().setContextClassLoader(mainClassLoader);
          pushSourceContextDelegate.commitOffset(entity, offset);
          return null;
        } finally {
          Thread.currentThread().setContextClassLoader(getDefinition().getStageClassLoader());
        }
      }
    });
  }

  public void setPushSourceContextDelegate(PushSourceContextDelegate delegate) {
    this.pushSourceContextDelegate = delegate;
  }

  public void setReportErrorDelegate(ReportErrorDelegate delegate) {
    this.reportErrorDelegate = delegate;
  }
}
