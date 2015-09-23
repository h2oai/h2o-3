import sys
sys.path.insert(1, "../../")
import h2o, tests

def javapredict_iris_drf():

    # optional parameters
    params = {'ntrees':100, 'max_depth':5, 'min_rows':10}
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train = h2o.import_file(h2o.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(h2o.locate("smalldata/iris/iris_train.csv"))
    x = ["sepal_len","sepal_wid","petal_len","petal_wid"]
    y = "species"

    tests.javapredict("random_forest", "class", train, test, x, y, **params)

if __name__ == "__main__":
    tests.run_test(sys.argv, javapredict_iris_drf)
