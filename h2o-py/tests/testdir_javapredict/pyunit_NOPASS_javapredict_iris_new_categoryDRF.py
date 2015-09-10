import sys
sys.path.insert(1, "../../")
import h2o, tests
import random

def javapredict_smallcat():

    # optional parameters
    params = {'ntrees':100, 'max_depth':5, 'min_rows':10}
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train = h2o.upload_file(h2o.locate("smalldata/iris/setosa_versicolor.csv"))
    test = h2o.upload_file(h2o.locate("smalldata/iris/virginica.csv"))
    x = [0,1,2,4]
    y = 3

    tests.javapredict("random_forest", "numeric", train, test, x, y, **params)

if __name__ == "__main__":
    tests.run_test(sys.argv, javapredict_smallcat)
