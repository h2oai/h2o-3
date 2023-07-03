from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import random

def javapredict_cars():

    # optional parameters
    params = {'ntrees':5000, 'max_depth':10, 'min_rows':1, 'learn_rate':0.1, 'balance_classes':random.sample([True,False],1)[0]}
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    train = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_nice_header.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_nice_header.csv"))
    x = ["name","economy", "displacement","power","weight","acceleration","year"]
    y = "cylinders"

    pyunit_utils.javapredict("gbm", "numeric", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_cars)
else:
    javapredict_cars()
