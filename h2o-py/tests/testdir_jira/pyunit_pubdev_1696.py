import sys
sys.path.insert(1, "../../")
import h2o, tests

def pubdev_1696():
    

    iris = h2o.import_file(tests.locate("smalldata/iris/iris.csv"))

    try:
        h2o.gbm(x=iris[0:3], y=iris[3], nfolds=-99)
        assert False, "expected an error"
    except EnvironmentError:
        assert True

if __name__ == "__main__":
    tests.run_test(sys.argv, pubdev_1696)
