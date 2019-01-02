app.controller('blogCtrl', ['$scope', '$http', '$window', '$timeout', '$filter', function ($scope, $http, $window, $timeout, $filter) {

    // Define all variables with data binding
    $scope.limit = 7;
    $scope.searchString = '';
    $scope.category = '';
    $scope.tag = '';
    $scope.filtered = '';
    $http({
      "method": "get",
      "url": "/wp-content/uploads/blog-cache.json"
    }).then(function (response) {
      $scope.items = response.data;
      $scope.tagResult();
    }, function (error) {
      console.error('There was some problem in loading example.json.');
    });

    $scope.emptyCheck = function () {
      if ($scope.filtered.length == 0) {
        $scope.renderMasonry();
      }
    }

    $scope.imageLoaded = function () {
      angular.element("body.h2o .blog-grid .loader").hide();
      angular.element('body.h2o .blog-grid').css('min-height', '');
      angular.element('body.h2o .blog-grid .grid-container .block-container .bg-img img').removeAttr('style');
      $timeout(function () {
        $scope.renderMasonry();
        angular.element('body.h2o .blog-grid .grid-container .block-container.inner-content-block').each(function () {
          angular.element(this).find(".bg-img").imageResponsive();
        });
        angular.element("body.h2o .blog-grid .grid-container, body.h2o .blog-grid .load-more-container").css("opacity", "1");
      }, 50);
    }

    $scope.tagResult = function () {
      var params = window.location.search;
      params = params.split('=');
      if (params[0] == "?tag") {
        $scope.tag = params[1];
      }
    }

    $scope.renderSearch = function (str) {
      if (!str) {
        $scope.searchString = '';
      } else {
        $scope.searchString = str.toLowerCase();
      }
      $timeout(function () {
        $scope.renderMasonry();
      }, 50);
    }

    $scope.loadmore = function () {
      $scope.limit = $scope.limit + 7;
      $timeout(function () {
        $scope.emptyCheck();
      }, 50);
    }

    $scope.categoryChange = function (str) {
      $scope.category = str;
      $timeout(function () {
        $scope.renderMasonry();
      }, 50);
    }

    $scope.renderMasonry = function () {
      angular.element('body.h2o .blog-grid .grid-container').masonry('reloadItems');
      angular.element('body.h2o .blog-grid .grid-container').masonry('layout');
    }
  }]);