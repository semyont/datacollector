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

/**
 * Home module for displaying home page content.
 */

angular
  .module('dataCollectorApp.home')
  .config(['$routeProvider', function ($routeProvider) {
    $routeProvider
      .when('/', {
        templateUrl: 'app/home/home.tpl.html',
        controller: 'HomeController',
        resolve: {
          myVar: function(authService) {
            return authService.init();
          }
        },
        data: {
          authorizedRoles: ['admin', 'creator', 'manager', 'guest']
        }
      });
  }])
  .controller('HomeController', function ($scope, $rootScope, $routeParams, $q, $modal, $location, pipelineService, api,
                                          configuration, pipelineConstant, Analytics, $route, $translate) {
    $location.search('auth_token', null);
    $location.search('auth_user', null);

    if ($routeParams.errors) {
      $rootScope.common.errors = [$routeParams.errors];
      //$location.search('errors', null);
    } else {
      $rootScope.common.errors = [];
    }

    var pipelinesLimit = 30;

    var PIPELINE_ACTIVE_STATUSES = [
      'CONNECTING',
      'DISCONNECTING',
      'FINISHING',
      'RETRY',
      'RUNNING',
      'STARTING',
      'STOPPING'
    ];

    angular.extend($scope, {
      loaded: false,
      totalPipelinesCount: 0,
      filteredPipelines: [],
      header: {
        pipelineGridView: $rootScope.$storage.pipelineListState.gridView,
        sortColumn: 'LAST_MODIFIED',
        sortReverse: true,
        searchInput: $scope.$storage.pipelineListState.searchInput,
        showNameColumn: $scope.$storage.pipelineListState.showNameColumn
      },
      selectedPipelineMap: {},
      selectedPipelineList: [],
      allSelected: false,
      showDetails: false,
      hideLibraryPanel: false,
      currentOffset: 0,
      pageSize: 50,
      totalCount: 0,
      showLoadMore: false,
      fetching: true,

      toggleLibraryPanel: function () {
        $scope.hideLibraryPanel = !$scope.hideLibraryPanel;
      },

      selectPipelineLabel: function(pipelineLabel) {
        $scope.selectedPipelineLabel = pipelineLabel;
        $scope.updateFilteredPipelines(0);
        $scope.unSelectAll();
      },

      updateFilteredPipelines: function(offset) {
        $scope.fetching = true;
        $scope.showLoadMore = false;
        var searchInput = ($scope.header.searchInput || '').trim();
        var regex = new RegExp(searchInput, 'i');
        $scope.$storage.pipelineListState.searchInput = searchInput;

        if (offset == 0) {
          $scope.filteredPipelines = [];
          $rootScope.common.pipelineStatusMap = {};
        }

        api.pipelineAgent.getPipelines(
          searchInput,
          $scope.selectedPipelineLabel,
          offset,
          $scope.pageSize,
          $scope.header.sortColumn,
          $scope.header.sortReverse ? 'DESC' : 'ASC',
          true
        ).then(
          function (res) {
            var pipelineInfoList = res.data[0];
            var statusList = res.data[1];

            $scope.filteredPipelines.push.apply($scope.filteredPipelines, pipelineInfoList);


            angular.forEach(statusList, function (status) {
              $rootScope.common.pipelineStatusMap[status.name] = status;
            });

            $scope.totalCount = res.headers('TOTAL_COUNT');

            $scope.showLoadMore = $scope.filteredPipelines.length < $scope.totalCount;
            $scope.currentOffset = offset + pipelineInfoList.length;

            $scope.fetching = false;
          },
          function (res) {
            $rootScope.common.errors = [res.data];
            $scope.fetching = false;
            $scope.showLoadMore = false;
          }
        );
      },

      /**
       * Refresh pipelines
       */
      refreshPipelines: function() {
        $scope.unSelectAll();
        $scope.updateFilteredPipelines(0);
      },

      /**
       * Add New Pipeline Configuration
       */
      addPipelineConfig: function() {
        pipelineService.addPipelineConfigCommand();
      },

      /**
       * Import Pipeline Configuration
       */
      importPipelineConfig: function(pipelineInfo, $event) {
        pipelineService.importPipelineConfigCommand(pipelineInfo, $event);
      },

      /**
       * Delete Pipeline Configuration
       */
      deletePipelineConfig: function(pipelineInfo, $event) {
        pipelineService.deletePipelineConfigCommand(pipelineInfo, $rootScope.common.pipelineStatusMap, $event)
          .then(function(pipelines) {
            $scope.updateFilteredPipelines(0);
          });
      },

      /**
       * Delete all selected Pipelines
       */
      deleteSelectedPipeline: function() {
        var selectedPipelineList = $scope.selectedPipelineList;
        var selectedPipelineInfoList = [];
        var validationIssues = [];
        angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
          if (selectedPipelineList.indexOf(pipelineInfo.name) !== -1) {
            var pipelineStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
            if (pipelineStatus && pipelineStatus.name === pipelineInfo.name &&
              _.contains(['RUNNING', 'STARTING', 'CONNECT_ERROR', 'RETRY', 'STOPPING'], pipelineStatus.status)) {
              validationIssues.push('Delete operation is not supported for Pipeline "' +
                pipelineInfo.name + '" with state ' +  pipelineStatus.status );
            }
            selectedPipelineInfoList.push(pipelineInfo);
          }
        });

        if (validationIssues.length > 0) {
          $rootScope.common.errors = validationIssues;
          return;
        }

        $rootScope.common.errors = [];

        pipelineService.deletePipelineConfigCommand(selectedPipelineInfoList, $rootScope.common.pipelineStatusMap)
          .then(function(pipelines) {
            $scope.updateFilteredPipelines(0);
            $scope.unSelectAll();
          });

      },

      /**
       * Duplicate Pipeline Configuration
       */
      duplicatePipelineConfig: function(pipelineInfo, $event) {
        pipelineService.duplicatePipelineConfigCommand(pipelineInfo, $event)
          .then(function() {
            $scope.refreshPipelines();
          });
      },

      /**
       * Duplicate Selected Pipeline
       */
      duplicatePipelines: function() {
        if ($scope.selectedPipelineList && $scope.selectedPipelineList.length > 0) {
          var selectedPipeline = _.find($scope.filteredPipelines, function(pipeline) {
            return $scope.selectedPipelineList[0] === pipeline.name
          });
          pipelineService.duplicatePipelineConfigCommand(selectedPipeline)
            .then(function(pipelines) {
              $scope.refreshPipelines();
            });
        }
      },

      /**
       * Share Pipeline Configuration
       */
      sharePipelineConfig: function(pipelineInfo, $event) {
        pipelineService.sharePipelineConfigCommand(pipelineInfo, $event);
      },

      /**
       * Share Selected Pipeline
       */
      shareSelectedPipelineConfig: function () {
        if ($scope.selectedPipelineList && $scope.selectedPipelineList.length > 0) {
          var selectedPipeline = _.find($scope.filteredPipelines, function(pipeline) {
            return $scope.selectedPipelineList[0] === pipeline.name
          });
          pipelineService.sharePipelineConfigCommand(selectedPipeline);
        }
      },

      /**
       * Reset Offset to Origin for selected pipelines
       */
      resetOffsetForSelectedPipelines: function() {
        $rootScope.common.trackEvent(
            pipelineConstant.BUTTON_CATEGORY,
            pipelineConstant.CLICK_ACTION,
            'Reset Offsets',
            1
        );

        var selectedPipelineList = $scope.selectedPipelineList;
        var selectedPipelineInfoList = [];
        var validationIssues = [];
        angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
          if (selectedPipelineList.indexOf(pipelineInfo.name) !== -1) {
            var pipelineStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
            if (pipelineStatus && pipelineStatus.name === pipelineInfo.name &&
                _.contains(PIPELINE_ACTIVE_STATUSES, pipelineStatus.status)) {
              validationIssues.push('Reset Origin operation is not supported for Pipeline "' +
                  pipelineInfo.name + '" with state ' +  pipelineStatus.status);
            }
            selectedPipelineInfoList.push(pipelineInfo);
          }
        });

        if (validationIssues.length > 0) {
          $rootScope.common.errors = validationIssues;
          return;
        }
        $rootScope.common.errors = [];

        var modalInstance = $modal.open({
          templateUrl: 'app/home/resetOffset/resetOffset.tpl.html',
          controller: 'ResetOffsetModalInstanceController',
          size: '',
          backdrop: 'static',
          resolve: {
            pipelineInfo: function () {
              return selectedPipelineInfoList;
            },
            originStageDef: function() {
              return null;
            }
          }
        });
      },

      /**
       * Add labels to selected pipelines
       */
      addLabelsToSelectedPipelines: function() {
        $rootScope.common.trackEvent(
            pipelineConstant.BUTTON_CATEGORY,
            pipelineConstant.CLICK_ACTION,
            'Add labels to selected pipelines',
            1
        );

        var selectedPipelineList = $scope.selectedPipelineList;
        var selectedPipelineInfoList = [];
        var validationIssues = [];
        angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
          if (selectedPipelineList.indexOf(pipelineInfo.name) !== -1) {
            var pipelineStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
            if (pipelineStatus && pipelineStatus.name === pipelineInfo.name &&
                _.contains(PIPELINE_ACTIVE_STATUSES, pipelineStatus.status)) {
              validationIssues.push('Add Label is not supported for Pipeline "' +
                  pipelineInfo.name + '" with state ' +  pipelineStatus.status );
            }
            selectedPipelineInfoList.push(pipelineInfo);
          }
        });

        if (validationIssues.length > 0) {
          $rootScope.common.errors = validationIssues;
          return;
        }
        $rootScope.common.errors = [];

        var modalInstance = $modal.open({
          templateUrl: 'app/home/header/addLabel/addLabelConfirmation.tpl.html',
          controller: 'AddLabelConfirmationModalInstanceController',
          size: '',
          backdrop: 'static',
          resolve: {
            pipelineInfoList: function () {
              return selectedPipelineInfoList;
            }
          }
        });

        modalInstance.result.then(function(res) {
          angular.forEach(res.successEntities, function(pipelineName) {
            var pipeline = _.find($scope.filteredPipelines, function(p) { return p.name === pipelineName });
            var mergedLabels = (pipeline.metadata.labels || []).concat(res.labels);
            pipeline.metadata.labels = _(mergedLabels).uniq();
          });
        }, function () {});
      },

      /**
       * Export link command handler
       */
      exportPipelineConfig: function(pipelineInfo, includeDefinitions, $event) {
        $event.stopPropagation();
        api.pipelineAgent.exportPipelineConfig(pipelineInfo.name, includeDefinitions);
      },

      /**
       * Export link command handler
       */
      exportSelectedPipelines: function(includeDefinitions) {
        var selectedPipelineList = $scope.selectedPipelineList;
        if (includeDefinitions) {
          // Export for DPM supports only for valid pipelines
          var validationIssues = [];
          angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
            if (selectedPipelineList.indexOf(pipelineInfo.name) !== -1 && !pipelineInfo.valid) {
              validationIssues.push('Pipeline "' + pipelineInfo.name + '" is not valid');
            }
          });

          if (validationIssues.length > 0) {
            $rootScope.common.errors = validationIssues;
            return;
          }
          $rootScope.common.errors = [];
        }
        api.pipelineAgent.exportSelectedPipelines(selectedPipelineList, includeDefinitions);
      },

      /**
       * Download Remote Pipeline Configuration
       */
      downloadRemotePipelineConfig: function($event) {
        var existingDPMPipelineIds = [];
        angular.forEach($scope.filteredPipelines, function (pipelineInfo) {
          if (pipelineInfo.metadata && pipelineInfo.metadata['dpm.pipeline.id']) {
            existingDPMPipelineIds.push(pipelineInfo.metadata['dpm.pipeline.id']);
          }
        });

        pipelineService.downloadRemotePipelineConfigCommand($event, existingDPMPipelineIds)
          .then(function() {
            $route.reload();
          });
      },

      /**
       * Open pipeline
       * @param pipeline
       */
      openPipeline: function(pipeline) {
        $location.path('/collector/pipeline/' + pipeline.name);
      },

      /**
       * On Start Pipeline button click.
       *
       */
      startPipeline: function(pipelineInfo, $event) {
        if ($event) {
          $event.stopPropagation();
        }

        $rootScope.common.trackEvent(
          pipelineConstant.BUTTON_CATEGORY,
          pipelineConstant.CLICK_ACTION,
          'Start Pipeline',
          1
        );

        if ($rootScope.common.pipelineStatusMap[pipelineInfo.name].state !== 'RUNNING') {
          api.pipelineAgent.startPipeline(pipelineInfo.name, 0).
          then(
            function (res) {
              var currentStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
              if (!currentStatus || (res.data && currentStatus.timeStamp < res.data.timeStamp)) {
                $rootScope.common.pipelineStatusMap[pipelineInfo.name] = res.data;
              }
            },
            function (res) {
              $rootScope.common.errors = [res.data];
            }
          );
        } else {
          $translate('home.graphPane.startErrorMessage', {
            name: pipelineInfo.name
          }).then(function(translation) {
            $rootScope.common.errors = [translation];
          });
        }
      },

      /**
       * On Start Pipelines button click.
       *
       */
      startSelectedPipelines: function() {
        $rootScope.common.trackEvent(
          pipelineConstant.BUTTON_CATEGORY,
          pipelineConstant.CLICK_ACTION,
          'Start Pipelines',
          1
        );

        var selectedPipelineList = $scope.selectedPipelineList;
        $rootScope.common.errors = [];

        api.pipelineAgent.startPipelines(selectedPipelineList).success(function(res) {
          angular.forEach(res.successEntities, function(pipelineState) {
            var currentStatus = $rootScope.common.pipelineStatusMap[pipelineState.name];
            if (!currentStatus || currentStatus.timeStamp < pipelineState.timeStamp) {
              $rootScope.common.pipelineStatusMap[pipelineState.name] = pipelineState;
            }
          });

          $rootScope.common.errors = res.errorMessages;
        }).error(function(data) {
          $rootScope.common.errors = [data];
        });
      },

      /**
       * On Stop Pipeline button click.
       *
       */
      stopPipeline: function(pipelineInfo, forceStop, $event) {
        if ($event) {
          $event.stopPropagation();
        }

        $rootScope.common.trackEvent(
          pipelineConstant.BUTTON_CATEGORY,
          pipelineConstant.CLICK_ACTION,
          'Stop Pipeline',
          1
        );
        var modalInstance = $modal.open({
          templateUrl: 'app/home/header/stop/stopConfirmation.tpl.html',
          controller: 'StopConfirmationModalInstanceController',
          size: '',
          backdrop: 'static',
          resolve: {
            pipelineInfo: function () {
              return pipelineInfo;
            },
            forceStop: function() {
              return forceStop;
            }
          }
        });

        modalInstance.result.then(function(status) {
          var currentStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
          if (!currentStatus || (status && currentStatus.timeStamp < status.timeStamp)) {
            $rootScope.common.pipelineStatusMap[pipelineInfo.name] = status;
          }

          var alerts = $rootScope.common.alertsMap[pipelineInfo.name];

          if (alerts) {
            delete $rootScope.common.alertsMap[pipelineInfo.name];
            $rootScope.common.alertsTotalCount -= alerts.length;
          }
        }, function () {

        });
      },

      /**
       * On Stop Pipelines button click.
       */
      stopSelectedPipelines: function(forceStop) {
        $rootScope.common.trackEvent(
          pipelineConstant.BUTTON_CATEGORY,
          pipelineConstant.CLICK_ACTION,
          'Stop Pipelines',
          1
        );

        var selectedPipelineList = $scope.selectedPipelineList;
        var selectedPipelineInfoList = [];

        angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
          if (selectedPipelineList.indexOf(pipelineInfo.name) !== -1) {
            selectedPipelineInfoList.push(pipelineInfo);
          }
        });
        $rootScope.common.errors = [];

        var modalInstance = $modal.open({
          templateUrl: 'app/home/header/stop/stopConfirmation.tpl.html',
          controller: 'StopConfirmationModalInstanceController',
          size: '',
          backdrop: 'static',
          resolve: {
            pipelineInfo: function () {
              return selectedPipelineInfoList;
            },
            forceStop: function() {
              return forceStop;
            }
          }
        });

        modalInstance.result.then(function(res) {
          angular.forEach(res.successEntities, function(pipelineState) {
            var currentStatus = $rootScope.common.pipelineStatusMap[pipelineState.name];
            if (!currentStatus || currentStatus.timeStamp < pipelineState.timeStamp) {
              $rootScope.common.pipelineStatusMap[pipelineState.name] = pipelineState;
            }
            var alerts = $rootScope.common.alertsMap[pipelineState.name];
            if (alerts) {
              delete $rootScope.common.alertsMap[pipelineState.name];
              $rootScope.common.alertsTotalCount -= alerts.length;
            }
          });
        }, function () {});
      },

      /**
       * On Publish Pipelines Click
       */
      publishSelectedPipelines: function() {
        $rootScope.common.trackEvent(
          pipelineConstant.BUTTON_CATEGORY,
          pipelineConstant.CLICK_ACTION,
          'Publish Pipelines',
          1
        );

        var selectedPipelineList = $scope.selectedPipelineList;
        var selectedPipelineInfoList = [];
        var validationIssues = [];
        angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
          if (selectedPipelineList.indexOf(pipelineInfo.name) !== -1) {
            var pipelineStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
            if (pipelineStatus && pipelineStatus.name === pipelineInfo.name) {
              if (!pipelineInfo.valid) {
                validationIssues.push('Publish operation is not supported for Invalid Pipeline - ' + pipelineInfo.name);
              } else if (pipelineStatus.attributes && pipelineStatus.attributes.IS_REMOTE_PIPELINE) {
                validationIssues.push('Publish operation is not supported for Remote Pipeline "' + pipelineInfo.name);
              }
              selectedPipelineInfoList.push(pipelineInfo);
            }
          }
        });

        if (validationIssues.length > 0) {
          $rootScope.common.errors = validationIssues;
          return;
        }

        $rootScope.common.errors = [];

        pipelineService.publishPipelineCommand(selectedPipelineInfoList)
          .then(function() {
            $scope.refreshPipelines();
          });
      },

      /**
       * Return pipeline alerts for tooltip
       *
       * @param pipelineAlerts
       */
      getPipelineAlerts: function(pipelineAlerts) {
        var alertMsg ='<span class="stage-errors-tooltip">';
        if (pipelineAlerts) {
          angular.forEach(pipelineAlerts, function(alert) {
            if (alert.ruleDefinition.family === 'drift' && alert.gauge.value.alertTexts &&
              alert.gauge.value.alertTexts.length) {
              //Data Drift Alert
              alertMsg += alert.gauge.value.alertTexts.join('<br>') + '<br>';
            } else {
              //General Data Alert
              alertMsg += alert.ruleDefinition.alertText + '<br>';
            }
          });
        }
        alertMsg += '</span>';
        return alertMsg;
      },

      /**
       * On Select All check box select
       */
      selectAll: function () {
        $scope.selectedPipelineList = [];
        $scope.selectedPipelineMap = {};
        angular.forEach($scope.filteredPipelines, function(pipelineInfo) {
          $scope.selectedPipelineList.push(pipelineInfo.name);
          $scope.selectedPipelineMap[pipelineInfo.name] = true;
        });
        $scope.allSelected = true;
      },

      /**
       * On UnSelect All check box select
       */
      unSelectAll: function () {
        $scope.selectedPipelineList = [];
        $scope.selectedPipelineMap = {};
        $scope.allSelected = false;
        $scope.limit = pipelinesLimit;
      },

      /**
       * On Selecting Individual Pipeline checkbox
       * @param pipeline
       * @param $event
       */
      selectPipeline: function(pipeline, $event) {
        $scope.selectedPipelineMap[pipeline.name] = true;
        $scope.selectedPipelineList.push(pipeline.name);
        if ($event.shiftKey) {
          var filteredPipelines = $scope.filteredPipelines;
          var index = _.findIndex(filteredPipelines, function(p) {
            return pipeline.name === p.name;
          });
          if (index != -1) {
            while (index > 0) {
              var prevPipeline = $scope.filteredPipelines[--index];
              if ($scope.selectedPipelineMap[prevPipeline.name]) {
                break;
              }
              $scope.selectedPipelineMap[prevPipeline.name] = true;
              $scope.selectedPipelineList.push(prevPipeline.name);
            }
          }

          $rootScope.common.clearTextSelection();
        }
      },

      /**
       * On UnSelecting Individual Pipeline checkbox
       * @param pipeline
       */
      unSelectPipeline: function(pipeline) {
        $scope.selectedPipelineMap[pipeline.name] = false;
        var index = $scope.selectedPipelineList.indexOf(pipeline.name);
        if (index != -1) {
          $scope.selectedPipelineList.splice(index, 1);
        }
        $scope.allSelected = false;
      },

      /**
       * On Show Details link click
       */
      showPipelineDetails: function () {
        $scope.showDetails = true;
      },

      /**
       * On Hide Details link click
       */
      hidePipelineDetails: function () {
        $scope.showDetails = false;
      },

      /**
       * On Clicking on Column Header
       * @param columnName
       */
      onSortColumnHeaderClick: function(columnName) {
        var sortReverse = !$scope.header.sortReverse;
        $scope.$storage.pipelineListState.sortColumn = columnName;
        $scope.$storage.pipelineListState.sortReverse = sortReverse;
        $scope.header.sortColumn = columnName;
        $scope.header.sortReverse = sortReverse;
        this.updateFilteredPipelines(0);
      },

      /**
       * Callback function when Show more link clicked.
       *
       * @param $event
       */
      onShowMoreClick: function($event) {
        $event.preventDefault();
        $scope.updateFilteredPipelines($scope.currentOffset);
      },

      /**
       * Returns true if pipeline is DPM controlled system pipeline
       * @param pipelineInfo
       */
      isSystemPipeline: function(pipelineInfo) {
        var pipelineStatus = $rootScope.common.pipelineStatusMap[pipelineInfo.name];
        return (
          pipelineStatus && pipelineStatus.name === pipelineInfo.name &&
          pipelineInfo.name.indexOf('System Pipeline for Job') === 0 &&
          pipelineStatus.attributes && pipelineStatus.attributes.IS_REMOTE_PIPELINE
        );
      },

      /**
       * Callback function when Show Name column menu item clicked
       */
      onToggleShowNameColumn: function() {
        var showNameColumn = !$scope.header.showNameColumn;
        $scope.$storage.pipelineListState.showNameColumn = showNameColumn;
        $scope.header.showNameColumn = showNameColumn;
      },

      /**
       * Register callback function to be called once all labels have been loaded
       * The callback is called with two params (system labels and custom labels)
       * @param callback
       */
      onLabelsLoaded: function(callback) {
        $scope.onLabelsLoadedCallback = callback;
      }

    });

    $q.all([
      api.pipelineAgent.getPipelinesCount(),
      configuration.init()
    ]).then(
      function (results) {
        $scope.loaded = true;
        $scope.totalPipelinesCount = results[0].data.count;

        if (configuration.isAnalyticsEnabled()) {
          Analytics.trackPage('/');
        }

        /**
         * Load pipeline list state preferences back from storage into view
         * If number of total pipelines > 100, sorting using status and for filtering pipelines which requires
         * Pipeline State is very expensive, so load sort and label preference only when number of pipelines < 100
         */
        if ($scope.totalPipelinesCount < 100) {
          if ($scope.$storage.pipelineListState.sortColumn) {
            $scope.header.sortColumn = $scope.$storage.pipelineListState.sortColumn;
            $scope.header.sortReverse = $scope.$storage.pipelineListState.sortReverse;
          }
        }

        if ($scope.$storage.pipelineListState.searchInput) {
          $scope.header.searchInput = $scope.$storage.pipelineListState.searchInput;
        }

        $scope.updateFilteredPipelines(0);
      },
      function () {
        $scope.loaded = true;
      }
    );

    $q.all([
      api.pipelineAgent.getSystemPipelineLabels(),
      api.pipelineAgent.getPipelineLabels()
    ]).then(
      function (results) {

        /**
         * Labels are loaded only once so they're sent to library.js
         */
        if (_.isFunction($scope.onLabelsLoadedCallback)) {
          $scope.onLabelsLoadedCallback(results[0].data, results[1].data);
        }

        /**
         * Make sure we only keep selection of still-existing labels
         */
        var labels = results[0].data.concat(results[1].data);
        if (labels.indexOf($scope.$storage.pipelineListState.selectedLabel) !== -1) {
          $scope.selectedPipelineLabel = $scope.$storage.pipelineListState.selectedLabel;
        } else {
          $scope.selectedPipelineLabel = 'system:allPipelines';
        }
      },
      function () {
        $scope.selectedPipelineLabel = 'system:allPipelines';
      }
    );

    $scope.$on('onAlertClick', function(event, alert) {
      if (alert && alert.pipelineName) {
        $rootScope.common.clickedAlert = alert;
        $location.path('/collector/pipeline/' + alert.pipelineName);
      }
    });

    var pipelineGridViewWatchListener = $scope.$watch('header.pipelineGridView', function() {
      $rootScope.$storage.pipelineListState.gridView = $scope.header.pipelineGridView;
    });

    $scope.$on('$destroy', function() {
      if (pipelineGridViewWatchListener) {
        pipelineGridViewWatchListener();
      }
    });

  });
