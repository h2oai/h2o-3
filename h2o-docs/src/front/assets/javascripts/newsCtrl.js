app.controller('newsCtrl', ['$scope', '$http', '$window', '$timeout', function ($scope, $http, $window, $timeout) {
    $scope.limit = 5;
    $scope.newsType = '';
    $scope.itemsPerRow = 2;
    // Request data from API
    $http({
        method: 'GET',
        url: '/wp-content/themes/h2o2018/templates/assets/data/news.json'
    }).then(function (response) {
        $scope.items = response.data;
        $timeout(function(){
        angular.element("body.h2o .generic-view-grid .loader").hide();
        angular.element('body.h2o .generic-view-grid').css('min-height','');
      },50);
    }, function (error) {
        console.error('There was some problem in loading news.json.');
    });

    $scope.loadmore = function () {
        $scope.limit = $scope.limit + 5;
    }

    $scope.news_filter = function (str) {
        if (str == '') {
            $scope.newsType = '';
        } else {
            $scope.newsType = str;
        }
    }
}]);