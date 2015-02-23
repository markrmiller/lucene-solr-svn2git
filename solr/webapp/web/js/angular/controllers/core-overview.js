/*
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/

solrAdminApp.controller('CoreOverviewController',
function($scope, $rootScope, $routeParams, Luke, CoreSystem, Update, Replication, Ping) {
  $scope.resetMenu("overview");
  $scope.refreshIndex = function() {
    Luke.index({core: $routeParams.core},
      function(data) {
        $scope.index = data.index;
        delete $scope.statsMessage;
      },
      function(error) {
        $scope.statsMessage = "Luke is not configured";
      }
    );
  };

  $scope.optimizeIndex = function(core) {
    Update.optimize({core: $routeParams.core},
      function(response) {
        $scope.refresh();
        delete $scope.indexMessage;
      },
      function(error) {
        $scope.statisticsDisabled = true;
        $scope.indexMessage = "Optimize broken.";
      });
  };

  $scope.refreshReplication = function() {
    Replication.details({core: $routeParams.core},
      function(data) {
        $scope.isSlave = data.details.isSlave == "true";
        $scope.isMaster = data.details.isMaster == "true";
        $scope.replication = data.details;
      },
      function(error) {
        $scope.replicationMessage = "Replication is not configured";
      });
  /*
      /replication?command=details&wt=json

              if( is_slave )
              {

                // warnings if slave version|gen doesn't match what's replicable
                if( data.indexVersion !== master_data.master.replicableVersion )
                {
                  $( '.version', details_element )
                    .addClass( 'diff' );
                }
                else
                {
                  $( '.version', details_element )
                    .removeClass( 'diff' );
                }

                if( data.generation !== master_data.master.replicableGeneration )
                {
                  $( '.generation', details_element )
                    .addClass( 'diff' );
                }
                else
                {
                  $( '.generation', details_element )
                    .removeClass( 'diff' );
                }
              }
            },

*/
  };

  $scope.refreshAdminExtra = function() {
  };

  $scope.refreshSystem = function() {
    CoreSystem.get({core: $routeParams.core},
      function(data) {
        $scope.core = data.core;
        delete $scope.systemMessage;
      },
      function(error) {
        $scope.systemMessage = "/admin/system Handler is not configured";
      }
    );
  };

  $scope.refreshPing = function() {
    Ping.status({core: $routeParams.core}, function(data) {
      $scope.healthcheckStatus = data.status == "enabled";
    }).$promise.catch(function(error) {
      if (error.status == 404) {
        $scope.healthcheckStatus = false;
      } else {
        $scope.healthcheckStatus = false;
        delete $rootScope.exception;
        $scope.healthcheckMessage = 'Ping request handler is not configured with a healthcheck file.';
      }
    });
  };

  $scope.toggleHealthcheck = function() {
    if ($scope.healthcheckStatus) {
      Ping.disable(
        function(data) {$scope.healthcheckStatus = false},
        function(error) {$scope.healthcheckMessage = error}
      );
    } else {
      Ping.enable(
        function(data) {$scope.healthcheckStatus = true},
        function(error) {$scope.healthcheckMessage = error}
      );
    }
  };

  $scope.refresh = function() {
    $scope.refreshIndex();
    $scope.refreshReplication();
    $scope.refreshAdminExtra();
    $scope.refreshSystem();
    $scope.refreshPing();
  };

  $scope.refresh();
});

/*******

// @todo admin-extra
    var core_basepath = this.active_core.attr( 'data-basepath' );
    var content_element = $( '#content' );

    content_element
      .removeClass( 'single' );

    if( !app.core_menu.data( 'admin-extra-loaded' ) )
    {
      app.core_menu.data( 'admin-extra-loaded', new Date() );

      $.get
      (
        core_basepath + '/admin/file/?file=admin-extra.menu-top.html&contentType=text/html;charset=utf-8',
        function( menu_extra )
        {
          app.core_menu
            .prepend( menu_extra );
        }
      );

      $.get
      (
        core_basepath + '/admin/file/?file=admin-extra.menu-bottom.html&contentType=text/html;charset=utf-8',
        function( menu_extra )
        {
          app.core_menu
            .append( menu_extra );
        }
      );
    }



////////////////////////////////// ADMIN EXTRA
        $.ajax
        (
          {
            url : core_basepath + '/admin/file/?file=admin-extra.html',
            dataType : 'html',
            context : $( '#admin-extra', dashboard_element ),
            beforeSend : function( xhr, settings )
            {
              $( 'h2', this )
                .addClass( 'loader' );

              $( '.message', this )
                .show()
                .html( 'Loading' );

              $( '.content', this )
                .hide();
            },
            success : function( response, text_status, xhr )
            {
              $( '.message', this )
                .hide()
                .empty();

              $( '.content', this )
                .show()
                .html( response );
            },
            error : function( xhr, text_status, error_thrown)
            {
              this
                .addClass( 'disabled' );

              $( '.message', this )
                .show()
                .html( 'We found no "admin-extra.html" file.' );
            },
            complete : function( xhr, text_status )
            {
              $( 'h2', this )
                .removeClass( 'loader' );
            }
          }
        );

***/
