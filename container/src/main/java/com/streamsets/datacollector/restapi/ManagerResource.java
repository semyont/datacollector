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
package com.streamsets.datacollector.restapi;

import com.streamsets.datacollector.execution.AclManager;
import com.streamsets.datacollector.execution.Manager;
import com.streamsets.datacollector.execution.PipelineState;
import com.streamsets.datacollector.execution.PipelineStatus;
import com.streamsets.datacollector.execution.Runner;
import com.streamsets.datacollector.execution.SnapshotInfo;
import com.streamsets.datacollector.execution.alerts.AlertInfo;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.main.UserGroupManager;
import com.streamsets.datacollector.restapi.bean.AlertInfoJson;
import com.streamsets.datacollector.restapi.bean.BeanHelper;
import com.streamsets.datacollector.restapi.bean.ErrorMessageJson;
import com.streamsets.datacollector.restapi.bean.MetricRegistryJson;
import com.streamsets.datacollector.restapi.bean.MultiStatusResponseJson;
import com.streamsets.datacollector.restapi.bean.PipelineStateJson;
import com.streamsets.datacollector.restapi.bean.RecordJson;
import com.streamsets.datacollector.restapi.bean.SampledRecordJson;
import com.streamsets.datacollector.restapi.bean.SnapshotDataJson;
import com.streamsets.datacollector.restapi.bean.SnapshotInfoJson;
import com.streamsets.datacollector.restapi.bean.UserJson;
import com.streamsets.datacollector.runner.PipelineRuntimeException;
import com.streamsets.datacollector.store.AclStoreTask;
import com.streamsets.datacollector.store.PipelineInfo;
import com.streamsets.datacollector.store.PipelineStoreTask;
import com.streamsets.datacollector.util.AuthzRole;
import com.streamsets.datacollector.util.ContainerError;
import com.streamsets.datacollector.util.PipelineException;
import com.streamsets.lib.security.http.SSOPrincipal;
import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.impl.Utils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/v1")
@Api(value = "manager")
@DenyAll
public class ManagerResource {
  private final String user;
  private final Manager manager;
  private final PipelineStoreTask store;
  private static final Logger LOG = LoggerFactory.getLogger(ManagerResource.class);

  @Inject
  public ManagerResource(
      Manager manager,
      Principal principal,
      PipelineStoreTask store,
      AclStoreTask aclStore,
      RuntimeInfo runtimeInfo,
      UserGroupManager userGroupManager
  ) {
    this.user = principal.getName();
    this.store = store;

    UserJson currentUser;
    if (runtimeInfo.isDPMEnabled()) {
      currentUser = new UserJson((SSOPrincipal)principal);
    } else {
      currentUser = userGroupManager.getUser(principal);
    }

    if (runtimeInfo.isAclEnabled()) {
      this.manager = new AclManager(manager, aclStore, currentUser);
    } else {
      this.manager = manager;
    }
  }

  @Path("/pipelines/status")
  @GET
  @ApiOperation(value = "Returns all Pipeline Status", response = PipelineStateJson.class,
    responseContainer = "Map", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response getAllPipelineStatus() throws PipelineException {
    RestAPIUtils.injectPipelineInMDC("*");
    List<PipelineState> pipelineStateList = manager.getPipelines();
    Map<String, PipelineStateJson> pipelineStateMap = new HashMap<>();
    for(PipelineState pipelineState: pipelineStateList) {
      pipelineStateMap.put(pipelineState.getName(), BeanHelper.wrapPipelineState(pipelineState, true));
    }
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(pipelineStateMap).build();
  }

  @Path("/pipeline/{pipelineName}/status")
  @GET
  @ApiOperation(value = "Returns Pipeline Status for the given pipeline", response = PipelineStateJson.class,
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response getPipelineStatus(
    @PathParam("pipelineName") String pipelineName,
    @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    if(pipelineName != null) {
      Runner runner = manager.getRunner(user, pipelineName, rev);
      if(runner != null) {
        return Response.ok()
            .type(MediaType.APPLICATION_JSON)
            .entity(BeanHelper.wrapPipelineState(runner.getState())).build();
      }
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/start")
  @POST
  @ApiOperation(value = "Start Pipeline", response = PipelineStateJson.class,
      authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response startPipeline(
      @PathParam("pipelineName") String pipelineName,
      @QueryParam("rev") @DefaultValue("0") String rev,
      @ApiParam(
          name = "runtimeConstants",
          value = "Runtime Constants to override Pipeline constants value"
      ) Map<String, Object> runtimeConstants
  ) throws StageException, PipelineException {
    if(pipelineName != null) {
      PipelineInfo pipelineInfo = store.getInfo(pipelineName);
      RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
      if (manager.isRemotePipeline(pipelineName, rev)) {
        throw new PipelineException(ContainerError.CONTAINER_01101, "START_PIPELINE", pipelineName);
      }
      try {
        Runner runner = manager.getRunner(user, pipelineName, rev);
        Utils.checkState(runner.getState().getExecutionMode() != ExecutionMode.SLAVE,
            "This operation is not supported in SLAVE mode");

        if (runtimeConstants != null) {
          Utils.checkState(runner.getState().getExecutionMode() == ExecutionMode.STANDALONE,
              Utils.format("This operation is not supported in {} mode", runner.getState().getExecutionMode()));
          runner.start(runtimeConstants);
        } else {
          runner.start();
        }
        return Response.ok()
            .type(MediaType.APPLICATION_JSON)
            .entity(BeanHelper.wrapPipelineState(runner.getState())).build();
      } catch (PipelineRuntimeException ex) {
        if (ex.getErrorCode() == ContainerError.CONTAINER_0165) {
          return Response.status(Response.Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON).entity(
              BeanHelper.wrapIssues(ex.getIssues())).build();
        } else {
          throw ex;
        }
      }
    }

    return Response.noContent().build();
  }

  @Path("/pipelines/start")
  @POST
  @ApiOperation(value = "Start multiple Pipelines", response = MultiStatusResponseJson.class,
      authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response startPipelines(List<String> pipelineNames) throws StageException, PipelineException {
    List<PipelineState> successEntities = new ArrayList<>();
    List<String> errorMessages = new ArrayList<>();

    for (String pipelineName: pipelineNames) {
      if (pipelineName != null) {
        PipelineInfo pipelineInfo = store.getInfo(pipelineName);
        RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());

        if (manager.isRemotePipeline(pipelineName, "0")) {
          errorMessages.add("Cannot start a remote pipeline: " + pipelineName);
          continue;
        }

        Runner runner = manager.getRunner(user, pipelineName, "0");
        try {
          Utils.checkState(runner.getState().getExecutionMode() != ExecutionMode.SLAVE,
              "This operation is not supported in SLAVE mode");

          runner.start();
          successEntities.add(runner.getState());

        } catch (Exception ex) {
          errorMessages.add("Failed starting pipeline: " + pipelineName + ". Error: " + ex.getMessage());
        }
      }
    }

    return Response.status(207)
        .type(MediaType.APPLICATION_JSON)
        .entity(new MultiStatusResponseJson<>(successEntities, errorMessages)).build();
  }

  @Path("/pipeline/{pipelineName}/stop")
  @POST
  @ApiOperation(value = "Stop Pipeline", response = PipelineStateJson.class,
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response stopPipeline(
    @PathParam("pipelineName") String pipelineName,
    @QueryParam("rev") @DefaultValue("0") String rev,
    @Context SecurityContext context
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    if (manager.isRemotePipeline(pipelineName, rev) && !context.isUserInRole(AuthzRole.ADMIN) &&
        !context.isUserInRole(AuthzRole.ADMIN_REMOTE)) {
      throw new PipelineException(ContainerError.CONTAINER_01101, "STOP_PIPELINE", pipelineName);
    }
    Runner runner = manager.getRunner(user, pipelineName, rev);
    Utils.checkState(runner.getState().getExecutionMode() != ExecutionMode.SLAVE,
      "This operation is not supported in SLAVE mode");
    runner.stop();
    return Response.ok()
        .type(MediaType.APPLICATION_JSON)
        .entity(BeanHelper.wrapPipelineState(runner.getState())).build();
  }

  @Path("/pipelines/stop")
  @POST
  @ApiOperation(value = "Stop multiple Pipelines", response = MultiStatusResponseJson.class,
      authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response stopPipelines(
      List<String> pipelineNames,
      @Context SecurityContext context
  ) throws StageException, PipelineException {
    List<PipelineState> successEntities = new ArrayList<>();
    List<String> errorMessages = new ArrayList<>();

    for (String pipelineName: pipelineNames) {
      if (pipelineName != null) {
        PipelineInfo pipelineInfo = store.getInfo(pipelineName);
        RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());

        if (manager.isRemotePipeline(pipelineName, "0") && !context.isUserInRole(AuthzRole.ADMIN) &&
            !context.isUserInRole(AuthzRole.ADMIN_REMOTE)) {
          errorMessages.add("Cannot stop a remote pipeline: " + pipelineName);
          continue;
        }
        Runner runner = manager.getRunner(user, pipelineName, "0");
        try {
          Utils.checkState(runner.getState().getExecutionMode() != ExecutionMode.SLAVE,
              "This operation is not supported in SLAVE mode");
          runner.stop();
          successEntities.add(runner.getState());

        } catch (Exception ex) {
          errorMessages.add("Failed stopping pipeline: " + pipelineName + ". Error: " + ex.getMessage());
        }
      }
    }

    return Response.status(207)
        .type(MediaType.APPLICATION_JSON)
        .entity(new MultiStatusResponseJson<>(successEntities, errorMessages)).build();
  }

  @Path("/pipeline/{pipelineName}/forceStop")
  @POST
  @ApiOperation(value = "Force Stop Pipeline", response = PipelineStateJson.class,
      authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response forceStopPipeline(
      @PathParam("pipelineName") String pipelineName,
      @QueryParam("rev") @DefaultValue("0") String rev,
      @Context SecurityContext context) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    if (manager.isRemotePipeline(pipelineName, rev)) {
      if (!context.isUserInRole(AuthzRole.ADMIN) && !context.isUserInRole(AuthzRole.ADMIN_REMOTE)) {
        throw new PipelineException(ContainerError.CONTAINER_01101, "FORCE_QUIT_PIPELINE", pipelineName);
      }
    }
    Runner runner = manager.getRunner(user, pipelineName, rev);
    Utils.checkState(runner.getState().getExecutionMode() == ExecutionMode.STANDALONE,
        Utils.format("This operation is not supported in {} mode", runner.getState().getExecutionMode()));
    runner.forceQuit();
    return Response.ok()
        .type(MediaType.APPLICATION_JSON)
        .entity(BeanHelper.wrapPipelineState(runner.getState())).build();
  }

  @Path("/pipelines/forceStop")
  @POST
  @ApiOperation(value = "Force Stop multiple Pipelines", response = MultiStatusResponseJson.class,
      authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response forceStopPipelines(List<String> pipelineNames, @Context SecurityContext context) throws PipelineException {
    List<PipelineState> successEntities = new ArrayList<>();
    List<String> errorMessages = new ArrayList<>();

    for (String pipelineName: pipelineNames) {
      if (pipelineName != null) {

        PipelineInfo pipelineInfo = store.getInfo(pipelineName);
        RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
        if (manager.isRemotePipeline(pipelineName, "0")) {
          if (!context.isUserInRole(AuthzRole.ADMIN) && !context.isUserInRole(AuthzRole.ADMIN_REMOTE)) {
            errorMessages.add("Cannot force stop a remote pipeline: " + pipelineName);
            continue;
          }
        }

        Runner runner = manager.getRunner(user, pipelineName, "0");
        try {
          Utils.checkState(runner.getState().getExecutionMode() == ExecutionMode.STANDALONE,
              Utils.format("This operation is not supported in {} mode", runner.getState().getExecutionMode()));
          runner.forceQuit();
          successEntities.add(runner.getState());

        } catch (Exception ex) {
          errorMessages.add("Failed to force stop pipeline: " + pipelineName + ". Error: " + ex.getMessage());
        }
      }
    }

    return Response.status(207)
        .type(MediaType.APPLICATION_JSON)
        .entity(new MultiStatusResponseJson<>(successEntities, errorMessages)).build();
  }

  @Path("/pipeline/{pipelineName}/committedOffsets")
  @GET
  @ApiOperation(value = "Return Committed Offsets. Note: Returned offset format will change between releases.",
      authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getCommittedOffsets(
      @PathParam("pipelineName") String name,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(name);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, name, rev);
    return Response.ok(runner.getCommittedOffsets()).build();
  }

  @Path("/pipeline/{pipelineName}/resetOffset")
  @POST
  @ApiOperation(value = "Reset Origin Offset", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response resetOffset(
      @PathParam("pipelineName") String name,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(name);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    if (manager.isRemotePipeline(name, rev)) {
      throw new PipelineException(ContainerError.CONTAINER_01101, "RESET_OFFSET", name);
    }
    Runner runner = manager.getRunner(user, name, rev);
    runner.resetOffset();
    return Response.ok().build();
  }

  @Path("/pipelines/resetOffsets")
  @POST
  @ApiOperation(value = "Reset Origin Offset for multiple pipelines", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response resetOffsets(List<String> pipelineNames) throws PipelineException {
    for (String pipelineName: pipelineNames) {
      PipelineInfo pipelineInfo = store.getInfo(pipelineName);
      RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
      if (manager.isRemotePipeline(pipelineName, "0")) {
        throw new PipelineException(ContainerError.CONTAINER_01101, "RESET_OFFSETS", pipelineName);
      }
      Runner runner = manager.getRunner(user, pipelineName, "0");
      runner.resetOffset();
    }
    return Response.ok().build();
  }

  @Path("/pipeline/{pipelineName}/metrics")
  @GET
  @ApiOperation(value = "Return Pipeline Metrics", response = MetricRegistryJson.class,
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response getMetrics(
      @PathParam("pipelineName") String pipelineName,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    if(pipelineName != null) {
      Runner runner = manager.getRunner(user, pipelineName, rev);
      if (runner != null && runner.getState().getStatus().isActive()) {
        return Response.ok().type(MediaType.APPLICATION_JSON).entity(runner.getMetrics()).build();
      }
      if (runner != null) {
        LOG.debug("Status is " + runner.getState().getStatus());
      } else {
        LOG.debug("Runner is null");
      }
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/snapshot/{snapshotName}")
  @PUT
  @ApiOperation(value = "Capture Snapshot", authorizations = @Authorization(value = "basic"))
  @RolesAllowed({ AuthzRole.MANAGER, AuthzRole.ADMIN, AuthzRole.MANAGER_REMOTE, AuthzRole.ADMIN_REMOTE })
  public Response captureSnapshot(
      @PathParam("pipelineName") String pipelineName,
      @PathParam("snapshotName") String snapshotName,
      @QueryParam("snapshotLabel") String snapshotLabel,
      @QueryParam("rev") @DefaultValue("0") String rev,
      @QueryParam("batches") @DefaultValue("1") int batches,
      @QueryParam("batchSize") int batchSize,
      @QueryParam("startPipeline") @DefaultValue("false") boolean startPipeline,
      Map<String, Object> runtimeConstants
  ) throws PipelineException, StageException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if (startPipeline && runner != null) {
      runner.startAndCaptureSnapshot(runtimeConstants, snapshotName, snapshotLabel, batches, batchSize);
    } else {
      Utils.checkState(runner != null && runner.getState().getStatus() == PipelineStatus.RUNNING,
          "Pipeline doesn't exist or it is not running currently");
      runner.captureSnapshot(snapshotName, snapshotLabel, batches, batchSize);
    }
    return Response.ok().build();
  }


  @Path("/pipeline/{pipelineName}/snapshot/{snapshotName}")
  @POST
  @ApiOperation(value = "Capture Snapshot", authorizations = @Authorization(value = "basic"))
  @RolesAllowed({ AuthzRole.MANAGER, AuthzRole.ADMIN, AuthzRole.MANAGER_REMOTE, AuthzRole.ADMIN_REMOTE })
  public Response updateSnapshotLabel(
      @PathParam("pipelineName") String pipelineName,
      @PathParam("snapshotName") String snapshotName,
      @QueryParam("snapshotLabel") String snapshotLabel,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, pipelineName, rev);
    runner.updateSnapshotLabel(snapshotName, snapshotLabel);
    return Response.ok().build();
  }

  @Path("/pipelines/snapshots")
  @GET
  @ApiOperation(value = "Returns all Snapshot Info", response = SnapshotInfoJson.class, responseContainer = "List",
    authorizations = @Authorization(value = "basic"))
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.CREATOR,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.CREATOR_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getAllSnapshotsInfo() throws PipelineException {
    RestAPIUtils.injectPipelineInMDC("*");
    List<SnapshotInfo> snapshotInfoList = new ArrayList<>();
    for(PipelineState pipelineState: manager.getPipelines()) {
      Runner runner = manager.getRunner(user, pipelineState.getName(), pipelineState.getRev());
      if(runner != null) {
        snapshotInfoList.addAll(runner.getSnapshotsInfo());
      }
    }
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(BeanHelper.wrapSnapshotInfoNewAPI(
      snapshotInfoList)).build();
  }

  @Path("/pipeline/{pipelineName}/snapshots")
  @GET
  @ApiOperation(value = "Returns Snapshot Info for the given pipeline", response = SnapshotInfoJson.class,
    responseContainer = "List", authorizations = @Authorization(value = "basic"))
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.CREATOR,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.CREATOR_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getSnapshotsInfo(
      @PathParam("pipelineName") String pipelineName,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(BeanHelper.wrapSnapshotInfoNewAPI(
        runner.getSnapshotsInfo())).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/snapshot/{snapshotName}/status")
  @GET
  @ApiOperation(value = "Return Snapshot status", response = SnapshotInfoJson.class,
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getSnapshotStatus(
    @PathParam("pipelineName") String pipelineName,
    @PathParam("snapshotName") String snapshotName,
    @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(
        BeanHelper.wrapSnapshotInfoNewAPI(runner.getSnapshot(snapshotName).getInfo())).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/snapshot/{snapshotName}")
  @GET
  @ApiOperation(value = "Return Snapshot data", response = SnapshotDataJson.class,
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.CREATOR,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.CREATOR_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getSnapshot(
      @PathParam("pipelineName") String pipelineName,
      @PathParam("snapshotName") String snapshotName,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(runner.getSnapshot(snapshotName).getOutput()).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/snapshot/{snapshotName}")
  @DELETE
  @ApiOperation(value = "Delete Snapshot data", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response deleteSnapshot(
      @PathParam("pipelineName") String pipelineName,
      @PathParam("snapshotName") String snapshotName,
      @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      runner.deleteSnapshot(snapshotName);
    }
    return Response.ok().build();
  }

  @Path("/pipeline/{pipelineName}/history")
  @DELETE
  @ApiOperation(value = "Delete history by pipeline name", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response deleteHistory(
    @PathParam("pipelineName") String pipelineName,
    @QueryParam("rev") @DefaultValue("0") String rev
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    if (manager.isRemotePipeline(pipelineName, rev)) {
      throw new PipelineException(ContainerError.CONTAINER_01101, "DELETE_HISTORY", pipelineName);
    }
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      runner.deleteHistory();
    }
    return Response.ok().build();
  }

  @Path("/pipeline/{pipelineName}/errorRecords")
  @GET
  @ApiOperation(value = "Returns error records by stage instance name and size", response = RecordJson.class,
    responseContainer = "List", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getErrorRecords(
      @PathParam("pipelineName") String pipelineName,
      @QueryParam("rev") @DefaultValue("0") String rev,
      @QueryParam ("stageInstanceName") @DefaultValue("") String stageInstanceName,
      @QueryParam ("size") @DefaultValue("10") int size
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    size = size > 100 ? 100 : size;
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(
        BeanHelper.wrapRecords(runner.getErrorRecords(stageInstanceName, size))).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/errorMessages")
  @GET
  @ApiOperation(value = "Returns error messages by stage instance name and size", response = ErrorMessageJson.class,
   responseContainer = "List", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getErrorMessages(
      @PathParam("pipelineName") String pipelineName,
      @QueryParam("rev") @DefaultValue("0") String rev,
      @QueryParam ("stageInstanceName") @DefaultValue("") String stageInstanceName,
      @QueryParam ("size") @DefaultValue("10") int size
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    size = size > 100 ? 100 : size;
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(
        BeanHelper.wrapErrorMessages(runner.getErrorMessages(stageInstanceName, size))).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/history")
  @GET
  @ApiOperation(value = "Find history by pipeline name", response = PipelineStateJson.class, responseContainer = "List",
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @PermitAll
  public Response getHistory(
    @PathParam("pipelineName") String name,
    @QueryParam("rev") @DefaultValue("0") String rev,
    @QueryParam("fromBeginning") @DefaultValue("false") boolean fromBeginning) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(name);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    Runner runner = manager.getRunner(user, name, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(
        BeanHelper.wrapPipelineStatesNewAPI(runner.getHistory(), false)).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipeline/{pipelineName}/sampledRecords")
  @GET
  @ApiOperation(value = "Returns Sampled records by sample ID and size", response = SampledRecordJson.class,
    responseContainer = "List", authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response getSampledRecords(
    @PathParam("pipelineName") String pipelineName,
    @QueryParam("rev") @DefaultValue("0") String rev,
    @QueryParam ("sampleId") String sampleId,
    @QueryParam ("sampleSize") @DefaultValue("10") int sampleSize
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    sampleSize = sampleSize > 100 ? 100 : sampleSize;
    Runner runner = manager.getRunner(user, pipelineName, rev);
    if(runner != null) {
      return Response.ok().type(MediaType.APPLICATION_JSON).entity(
        BeanHelper.wrapSampledRecords(runner.getSampledRecords(sampleId, sampleSize))).build();
    }
    return Response.noContent().build();
  }

  @Path("/pipelines/alerts")
  @GET
  @ApiOperation(value = "Returns alerts triggered for all pipelines", response = AlertInfoJson.class,
    responseContainer = "List", authorizations = @Authorization(value = "basic"))
  @PermitAll
  public Response getAllAlerts()
    throws PipelineException {
    RestAPIUtils.injectPipelineInMDC("*");
    List<AlertInfo> alertInfoList = new ArrayList<>();
    for(PipelineState pipelineState: manager.getPipelines()) {
      Runner runner = manager.getRunner(user, pipelineState.getName(), pipelineState.getRev());
      if(runner != null && runner.getState().getStatus().isActive()) {
        alertInfoList.addAll(runner.getAlerts());
      }
    }
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(BeanHelper.wrapAlertInfoList(
      alertInfoList)).build();
  }


  @Path("/pipeline/{pipelineName}/alerts")
  @DELETE
  @ApiOperation(value = "Delete alert by Pipeline name, revision and Alert ID", response = Boolean.class,
    authorizations = @Authorization(value = "basic"))
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed({
      AuthzRole.MANAGER,
      AuthzRole.ADMIN,
      AuthzRole.MANAGER_REMOTE,
      AuthzRole.ADMIN_REMOTE
  })
  public Response deleteAlert(
    @PathParam("pipelineName") String pipelineName,
    @QueryParam("rev") @DefaultValue("0") String rev,
    @QueryParam("alertId") String alertId
  ) throws PipelineException {
    PipelineInfo pipelineInfo = store.getInfo(pipelineName);
    RestAPIUtils.injectPipelineInMDC(pipelineInfo.getTitle(), pipelineInfo.getName());
    return Response.ok().type(MediaType.APPLICATION_JSON).entity(
      manager.getRunner(user, pipelineName, rev).deleteAlert(alertId)).build();
  }

}
