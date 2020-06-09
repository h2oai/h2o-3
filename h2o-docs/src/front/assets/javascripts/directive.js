var usecase = [];
var industry = [];
//app.directive('industryfilter', ['$compile', function ($compile) {
//    return{
//      scope: true,
//      restrict: 'A',
//      replace: false,
//      link: function (scope, element, attr) {
//        element.bind('click', function ($event) {
//          var str = angular.element(this).attr('data-id');
//          var text = angular.element(this).text();
//          str = str.toLowerCase();
//          var found = jQuery.inArray(str, industry);
//          if (found >= 0) {
//            industry.splice(found, 1);
//            angular.element('.filter-info .filter-select h6[data-id='+ str +']').remove();
//            angular.element('.clear-box .filter-select h6[data-id='+ str +']').remove();
//          } else {
//            industry.push(str);
//            var el ='<h6 data-id=' + str + ' clearonebutton>'+text+'</h6>';
//            var compiledElement = $compile(el)(scope); 
//            var ncompiledElement = $compile(el)(scope);
//            angular.element('.filter-info .filter-select').append(ncompiledElement);
//            angular.element('.clear-box .filter-select').append(compiledElement);
//          }
//          scope.industry_filter(industry);
//          if (!scope.$$phase) {
//            scope.$apply();
//          }
//        });
//      }
//    };
//  }]);

/*
 * Directive for customer tabs for industry
 */
app.directive('industryfilter', ['$compile', function ($compile) {
    return{
      scope: true,
      restrict: 'A',
      replace: false,
      link: function (scope, element, attr) {
        element.bind('click', function ($event) {
          var str = angular.element(this).attr('data-id');
          str = str.toLowerCase();
          scope.industry_filter(str);
          if (!scope.$$phase) {
            scope.$apply();
          }
        });
      }
    };
  }]);

/*
 * Directive for customer usecase checkbox
 */
app.directive('usecases', ['$compile', function ($compile) {
    return{
      scope: true,
      restrict: 'A',
      replace: false,
      link: function (scope, element, attr) {
        element.bind('click', function ($event) {
          var str = angular.element(this).val();
          var text = angular.element(this).next('label').html();
          var found = jQuery.inArray(str, usecase);
          if (found >= 0) {
            usecase.splice(found, 1);
            angular.element('.clear-box .filter-select h6[data-id='+ str +']').remove();
          } else {
            usecase.push(str);
            var el ='<h6 data-id=' + str + ' clearonebutton>'+text+'</h6>';
            var compiledElement = $compile(el)(scope); 
            var ncompiledElement = $compile(el)(scope);
            angular.element('.clear-box .filter-select').append(compiledElement);
          }
          scope.usecase_filter(usecase);
          if (!scope.$$phase) {
            scope.$apply();
          }
        });
      }
    };
  }]);

/*
 * Directive for clear all on customer stories
 */
app.directive('clearbutton', ['$compile', function ($compile) {
    return{
      scope: true,
      restrict: 'A',
      replace: false,
      link: function (scope, element, attr) {
        element.bind('click', function ($event) {
//          industry= [];
          usecase = [];
          scope.clearAll();
          if (!scope.$$phase) {
            scope.$apply();
          }
        });
      }
    };
  }]);

/*
 * Directive for clear one by one on customer stories
 */
app.directive('clearonebutton', ['$compile', function ($compile) {
    return{
      scope: true,
      restrict: 'A',
      replace: false,
      link: function (scope, element, attr) {
        element.bind('click', function ($event) {
          var str = angular.element(this).attr('data-id');
          var found = jQuery.inArray(str, usecase);
//          var foundIn = jQuery.inArray(str, industry);
          if (found >= 0) {
            usecase.splice(found, 1);
            angular.element('.filter-info .check-box li input[id='+ str +']').prop('checked',false);
            scope.usecase_filter(usecase);
          }
//          else if(foundIn >=0){
//            industry.splice(found, 1);
//            scope.industry_filter(industry);
//          }
          angular.element('.filter-info .filter-select h6[data-id='+ str +']').remove();
          angular.element('.clear-box .filter-select h6[data-id='+ str +']').remove();
//          scope.industry_filter(industry);
          scope.usecase_filter(usecase);
            scope.$apply();
        });
      }
    };
  }]);

/*
 * Directive for finised the last element
 */
app.directive('onFinishRender', function ($timeout) {
    return {
        restrict: 'A',
        link: function (scope, element, attr) {
            if (scope.$last === true) {
                $timeout(function () {
                    scope.$emit(attr.onFinishRender);
                });
            }
        }
    }
});

/*
 * Directive for load the image
 */
app.directive('imageonload', function() {
    return {
        restrict: 'A',
        link: function(scope, element, attrs) {
            element.bind('load', function() {
                scope.imageLoaded();
            });
        }
    };
});

/*
 * Directive for tab on events page
 */
app.directive('eventfilter', ['$compile', function ($compile) {
    return{
      scope: true,
      restrict: 'A',
      replace: false,
      link: function (scope, element, attr) {
        element.bind('click', function ($event) {
          var str = angular.element(this).attr('data-id');
          str = str.toLowerCase();
          scope.event_filter(str);
          if (!scope.$$phase) {
            scope.$apply();
          }
        });
      }
    };
  }]);

app.directive('pastEvents', ['$compile', function ($compile) {
    return{
      scope: true,
      restrict: 'A',
      replace: false,
      link: function (scope, element, attr) {
        element.bind('click', function ($event) {
          var str = angular.element(this).attr('data-id');
          str = str.toLowerCase();
          scope.event_filter(str);
          if (!scope.$$phase) {
            scope.$apply();
          }
        });
      }
    };
  }]);


/*
 * Directive for tab on News page
 */
app.directive('newsfilter', ['$compile', function ($compile) {
    return{
        scope: true,
        restrict: 'A',
        replace: false,
        link: function (scope, element, attr) {
            element.bind('click', function ($event) {
                var str = angular.element(this).attr('data-id');
                str = str.toLowerCase();
                scope.news_filter(str);
                if (!scope.$$phase) {
                    scope.$apply();
                }
            });
        }
    };
}]);

/*
 * Directive for tab on usecases page
 */
app.directive('usecasesfilter', ['$compile', function ($compile) {
    return{
        scope: true,
        restrict: 'A',
        replace: false,
        link: function (scope, element, attr) {
            element.bind('click', function ($event) {
                var str = angular.element(this).attr('data-id');
                str = str.toLowerCase();
                scope.usecases_filter(str);
                if (!scope.$$phase) {
                    scope.$apply();
                }
            });
        }
    };
}]);