wkhomeControllers.controller('PublicationsController', ['$scope', '$window', 'globalData', 'sparqlQuery', 'searchData', '$routeParams', '$translate', '$uibModal',
  function($scope, $window, globalData, sparqlQuery, searchData, $routeParams, $translate, $uibModal) {

    $scope.author = {};
    $scope.authorURI = $routeParams.authorId;
    $scope.core = globalData.publicationsCore;

    $scope.showInfo = function(uri, type) {
      $uibModal.open({
        templateUrl: 'wkhome/partials/collectionTemplate.html',
        controller: JournalController,
        resolve: {
          collections: function() {
            return {
              collectionType: type,
              collectionUri: uri
            };
          }
        }
      });
    };

    var JournalController = function($scope, $uibModalInstance, collections, Journal) {
      $scope.journals = [];
      _.forEach(collections.collectionUri, function(uri){
        Journal.getJournal().get({uri: uri}, function(data){
          $scope.journals.push(data.response.docs);
          $scope.journals = _.flatten($scope.journals);
        });
      });
      $scope.type = collections.collectionType;
      $scope.dialogTitle = "";
    };

    var sparqlAuthor = globalData.PREFIX +
      "CONSTRUCT {" +
      "  ?author a foaf:Person ;" +
      "                    foaf:name ?name ;" +
      "                    schema:memberOf ?org;" +
      "                    foaf:img ?img." +
      "  ?org uc:city ?city ;" +
      "       uc:province ?province ;" +
      "       uc:fullName ?fullname ." +
      "} " +
      "WHERE{" +
      "  {" +
      "  	SELECT DISTINCT *" +
      "    WHERE { " +
      "      VALUES ?author { <" + $scope.authorURI + "> }" +
      "      GRAPH <" + globalData.centralGraph + "> {" +
      "        ?author a foaf:Person ;" +
      "                           foaf:name ?name ;" +
      "                           schema:memberOf ?org ." +
      "        OPTIONAL { ?author foaf:img ?img. }" +
      "      }" +
      "      GRAPH <" + globalData.organizationsGraph + "> {" +
      "        ?org uc:city ?city ;" +
      "        uc:province ?province ;" +
      "        uc:fullName ?fullname ." +
      "        FILTER (lang(?fullname) = '" + $translate.use() + "')" +
      "      }" +
      "    }" +
      "    LIMIT 1" +
      "  }" +
      "}";
    // waitingDialog.show("Searching: ");
    sparqlQuery.querySrv({
      query: sparqlAuthor
    }, function(rdf) {
      jsonld.compact(rdf, globalData.CONTEXT, function(error, compacted) {
        if (!error) {
          var entity = _.findWhere(compacted["@graph"], {
            "@type": "foaf:Person"
          });
          $scope.author.name = typeof(entity["foaf:name"]) === 'string' ? entity["foaf:name"] : _.first(entity["foaf:name"]);
          $scope.author.photo = entity["foaf:img"] ? entity["foaf:img"]["@id"] : undefined;
          $scope.author.institutions = [];
          _.each(entity["schema:memberOf"], function(v) {
            var provenance = typeof(v) === 'object' ?
              _.findWhere(compacted["@graph"], v) :
              _.findWhere(compacted["@graph"], {
                "@id": v
              })
            $scope.author.institutions.push({
              name: provenance['uc:fullName']['@value'],
              province: provenance["uc:city"],
              city: provenance["uc:province"]
            })
          });
        }
      });
    });

  }
]);
