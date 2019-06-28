wkhomeControllers.controller('subCluster', ['$scope', '$window', 'globalData', 'sparqlQuery', 'searchData', '$routeParams', 'Statistics', 'querySubcluster',
  function($scope, $window, globalData, sparqlQuery, searchData, $routeParams, Statistics, querySubcluster) {

    var cluster = $routeParams.cluster;
    var subcluster = $routeParams.subcluster;
 //  $scope.areaCombosub =  { "tag":"12313"}; 
      $scope.areaCombo = {};
    Statistics.query({
      id: 'keywords_frequencypub_gt4'
    }, function(data) {
      $scope.relatedtags = [];
      _.map(data["@graph"], function(keyword) {
        $scope.relatedtags.push({
          id: keyword["@id"],
          tag: keyword["rdfs:label"]["@value"]
        });
      });
    });

    $scope.changeCombo = function() {
      $scope.datacl = {};

      $scope.areaCombosub = {};
      $scope.datacl = {
        cluster: $scope.areaCombo.selected.id,
        subcluster: null
      };
      querySubcluster.query({
        id: $scope.areaCombo.selected.id
      }, function(data) {
        $scope.subtags = [];
        _.map(data.subclusters, function(keyword) {
          $scope.subtags.push({
            id: keyword["uri"],
            tag: keyword["label-en"]
          });
        });
      });
    }

    $scope.changeComboSub = function() {
      $scope.datacl = {};
      $scope.datacl = {
        cluster: $scope.areaCombo.selected.id,
        subcluster: $scope.areaCombosub.selected.id
      };
      console.log ($scope.areaCombosub);
    }

    if (cluster && subcluster) {
      $scope.areaCombo = cluster;
      $scope.changeCombo();
      $scope.areaCombosub = subcluster;
      $scope.changeComboSub();
    }

  }
]);
