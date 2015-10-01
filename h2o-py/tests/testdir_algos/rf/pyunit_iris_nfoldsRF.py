import sys
sys.path.insert(1, "../../../")
import h2o, tests

def iris_nfolds():
    
    

    iris = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))

    model = h2o.random_forest(y=iris[4], x=iris[0:4], ntrees=50, nfolds=5)
    model.show()
  
    # Can specify both nfolds >= 2 and validation = H2OParsedData at once
    try:
        h2o.random_forest(y=iris[4], x=iris[0:4], validation_y=iris[4], validation_x=iris[0:4], ntrees=50, nfolds=5)
        assert True
    except EnvironmentError:
        assert False, "expected an error"

if __name__ == "__main__":
  tests.run_test(sys.argv, iris_nfolds)
