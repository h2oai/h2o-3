

import h2o, tests

def iris_ignore():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris2.csv"))
  
    for maxx in range(4):
      model = h2o.random_forest(y=iris[4], x=iris[range(maxx+1)], ntrees=50, max_depth=100)
      model.show()


pyunit_test = iris_ignore

