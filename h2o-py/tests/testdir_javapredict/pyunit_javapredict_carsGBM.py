

import h2o, tests
import random

def javapredict_cars():

    # optional parameters
    params = {'ntrees':5000, 'max_depth':10, 'min_rows':1, 'learn_rate':0.1, 'balance_classes':random.sample([True,False],1)[0]}
    print "Parameter list:"
    for k,v in zip(params.keys(), params.values()): print "{0}, {1}".format(k,v)

    train = h2o.import_file(tests.locate("smalldata/junit/cars_nice_header.csv"))
    test = h2o.import_file(tests.locate("smalldata/junit/cars_nice_header.csv"))
    x = ["name","economy", "displacement","power","weight","acceleration","year"]
    y = "cylinders"

    tests.javapredict("gbm", "numeric", train, test, x, y, **params)


pyunit_test = javapredict_cars
