/*
 * Return unique items from the array
 */
app.filter('unique', function () {
  return function (collection, keyname) {
    var output = [],
            keys = [];
    angular.forEach(collection, function (item) {
      for (var i = 0; i < item.category.length; i++) {
        var key = item.category[keyname];
        if (keys.indexOf(key) === -1) {
          keys.push(key);
          output.push(item);
        }
      }

    });
    return output;
  };
});


/*
 * Return string replacing all spaces with -
 */
app.filter('spaceless', function () {
  return function (input) {
    if (input) {
      return input.replace(/\s+/g, '-');
    }
  };
});

/*
 * Wrap div on ready 
 */
app.filter('grouped', ['$rootScope', function ($rootScope) {
    makeSlices = function (items, count) {
      if (!count)
        count = 3;
      if (!angular.isArray(items) && !angular.isString(items))
        return items;

      var array = [];

      for (var i = 0; i < items.length; i++) {
        var chunkIndex = parseInt(i / count, 10);
        var isFirst = (i % count === 0);
        if (isFirst)
          array[chunkIndex] = [];
        array[chunkIndex].push(items[i]);
      }

      if (angular.equals($rootScope.arrayinSliceOf, array))
        return $rootScope.arrayinSliceOf;
      else
        $rootScope.arrayinSliceOf = array;

      return array;
    };
    return makeSlices;
  }]);

/*
 * Filter for Category object in Blog Json 
 */

app.filter('categoryFilter', ['$filter', function ($filter) {
    return function (items, category) {
      var result = [];
      if (category) {
        category = category.toLowerCase();
      } else{
        return items;
      }
      angular.forEach(items, function (item,key) {
        angular.forEach(item.category, function (ele) {
          if ((ele.slug == category)) {
            var found = jQuery.inArray(items[key], result);
              if (found < 0) {
                result.push(item);
              }
          }
        });
      });
      return result;
    }
  }]);

/*
 * Filter for Tags object in Blog Json 
 */

app.filter('tagFilter', ['$filter', function ($filter) {
    return function (items, tag) {
      var result = [];
      if (tag) {
        tag = tag.toLowerCase();
      } else{
        return items;
      }
      angular.forEach(items, function (item,key) {
        angular.forEach(item.tags, function (ele) {
          if ((ele.slug == tag)) {
            var found = jQuery.inArray(items[key], result);
              if (found < 0) {
                result.push(item);
              }
          }
        });
      });
      return result;
    }
  }]);

/*
 * Filter for Use Case object in Customer Stories Json 
 */
app.filter('useCaseFilter', ['$filter', function ($filter) {
    return function (items, usecase) {
      var result = [];
      if (usecase == '') {
        return items;
      }
      angular.forEach(items, function (item, key) {
        angular.forEach(item['use_case'], function (ele, index) {
          for (var i = 0; i <= usecase.length; i++) {
            if ((item['use_case'][index].slug == usecase[i])) {
              var found = jQuery.inArray(items[key], result);
              if (found < 0) {
                result.push(item);
              }
            }
          }
        });
      });
      return result;
    }
  }]);

/*
 * Filter for Industry object in Customer Stories Json 
*/

app.filter('industryFilter', ['$filter', function ($filter) {
    return function (items, industry) {
      var result = [];
      if (industry == '') {
        return items;
      }
      angular.forEach(items, function (item, key) {
        angular.forEach(item['industry'], function (ele, index) {
            if ((item['industry'][index].slug == industry)) {
              var found = jQuery.inArray(items[key], result);
              if (found < 0) {
                result.push(item);
              }
            }
        });
      });
      return result;
    }
  }]);

/*
 * Filter for Industry object in Customer Stories Json 
*/

app.filter('eventFilter', ['$filter', function ($filter) {
    return function (items, eventType) {
      var result = [];
      if (eventType == '') {
        return items;
      }
      angular.forEach(items, function (item, key) {
        angular.forEach(item['events_type'], function (ele, index) {
            if ((item['events_type'][index].slug == eventType)) {
              var found = jQuery.inArray(items[key], result);
              if (found < 0) {
                result.push(item);
              }
            }
        });
      });
      return result;
    }
  }]);


app.filter('newsFilter', ['$filter', function ($filter) {
    return function (items, newsType) {
        var result = [];
        if (newsType == '') {
            return items;
        }
        angular.forEach(items, function (item, key) {
            angular.forEach(item['news_type'], function (ele, index) {
                if ((item['news_type'][index].slug == newsType)) {
                    var found = jQuery.inArray(items[key], result);
                    if (found < 0) {
                        result.push(item);
                    }
                }
            });
        });
        return result;
    }
}]);

//app.filter('customerFilter', ['$filter', function ($filter) {
//        return function (items, industry, usecase) {
//            var result = [];
//            if (industry == '' && usecase == '') {
//                angular.forEach(items, function (item) {
//                    result.push(item);
//                });
//            } else if (industry !== '' && usecase == '') {
//                angular.forEach(items, function (item, key) {
//                    angular.forEach(item.industry, function (ele, index) {
//                        for (var i = 0; i < industry.length; i++) {
//                            if ((item.industry[index].slug === industry)) {
//                                var found = jQuery.inArray(items[key], result);
//                                if (found < 0) {
//                                    result.push(item);
//                                }
//                            }
//                        }
//                    });
//                });
//            } else if (industry == '' && usecase !== '') {
//                angular.forEach(items, function (item, key) {
//                    angular.forEach(item['use_case'], function (ele, index) {
//                        for (var i = 0; i <= usecase.length; i++) {
//                            if ((item['use_case'][index].slug == usecase[i])) {
//                                var found = jQuery.inArray(items[key], result);
//                                if (found < 0) {
//                                    result.push(item);
//                                }
//                            }
//                        }
//                    });
//                });
//            } else if (industry !== '' && usecase !== '') {
//                angular.forEach(items, function (item, key) {
//                    if (industry == '') {
//                        angular.forEach(item['use_case'], function (ele, index) {
//                            for (var i = 0; i <= usecase.length; i++) {
//                                if ((item['use_case'][index].slug == usecase[i])) {
//                                    angular.forEach(item.industry, function (el, newIndex) {
//                                        for (var j = 0; j < industry.length; j++) {
//                                            if ((item.industry[newIndex].slug === industry)) {
//                                                var found = jQuery.inArray(items[key], result);
//                                                if (found < 0) {
//                                                    result.push(item);
//                                                }
//                                            }
//                                        }
//                                    });
//                                }
//                            }
//                        });
//                    }
//                    else {
//                        angular.forEach(item.industry, function (el, newIndex) {
//                            for (var j = 0; j < industry.length; j++) {
//                                if ((item.industry[newIndex].slug === industry)) {
//                                    angular.forEach(item['use_case'], function (ele, index) {
//                                        for (var i = 0; i <= usecase.length; i++) {
//                                            if ((item['use_case'][index].slug == usecase[i])) {
//                                                var found = jQuery.inArray(items[key], result);
//                                                if (found < 0) {
//                                                    result.push(item);
//                                                }
//                                            }
//                                        }
//                                    });
//                                }
//                            }
//                        });
//                    }
//                });
//            }
//            angular.element('.customer-grid .col-three h6').matchHeight();
//            return result;
//        }
//    }]);