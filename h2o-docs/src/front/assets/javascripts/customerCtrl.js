app.controller('customerCtrl', ['$scope', '$http', '$window', '$timeout', function ($scope, $http, $window, $timeout) {

    // Define all variables with data binding
    $scope.limit = 12;    // Limit
    $scope.itemsPerRow = 3;
    $scope.windowWidth = 0;
    $scope.industry = '';
    $scope.usecase = '';
    // Request data from API
    $http({
      method: 'GET',
      url: '/wp-content/themes/h2o2018/templates/section/json/customers-json.php'
    }).then(function (response) {
      $scope.items = response.data;
      $timeout(function () {
        getItemsPerRow();
      },50);
    }, function (error) {
      console.error('There was some problem in loading customer-stories.json.');
    });

    $scope.$on('ngRepeatFinished', function (ngRepeatFinishedEvent) {
        angular.element("body.h2o .customer-grid .loader").hide();
        angular.element('body.h2o .customer-grid').css('min-height','inherit');
      $timeout(function () {
        setVideoPath();
        angular.element('.customer-grid .col-three h6').matchHeight();
          angular.element("body.h2o .customer-grid .col-three-wrap").css("opacity", "1");
      }, 50);
    });

    angular.element($window).bind('resize', function () {
        getItemsPerRow();
        setVideoPath();
        angular.element('.customer-grid .col-three h6').matchHeight();
      if (!$scope.$$phase) {
            $scope.$apply();
          }
    });

    $scope.clearAll = function () {
      $scope.usecase = '';
      angular.element('.filter-info .check-box li input').prop('checked', false);
      angular.element('.filter-info .filter-select h6').remove();
      angular.element('.clear-box .filter-select h6').remove();
    }

    $scope.industry_filter = function (str) {
      if (str == '') {
        $scope.industry = '';
      } else {
        $scope.industry = str;
      }
    }

    $scope.usecase_filter = function (str) {
      $scope.usecase = str;
    }
    
    getItemsPerRow = function () {
      if (window.innerWidth > 767 && $scope.itemsPerRow != 3) {
        $scope.itemsPerRow = 3;
        angular.element('.customer-grid .customer-info-div').css({'display': 'none'});
      } else if (window.innerWidth > 480 && window.innerWidth < 768 && $scope.itemsPerRow != 2) {
        $scope.itemsPerRow = 2;
        angular.element('.customer-grid .customer-info-div').css({'display': 'none'});
      } else if(window.innerWidth < 481 && $scope.itemsPerRow != 1) {
        $scope.itemsPerRow = 1;
        angular.element('.customer-grid .customer-info-div').css({'display': 'none'});
      }
    }
    
  }]);