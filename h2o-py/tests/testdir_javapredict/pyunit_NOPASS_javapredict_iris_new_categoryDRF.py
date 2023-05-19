from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



# PUBDEV-2055
def javapredict_smallcat():

    # optional parameters
    params = {'ntrees':100, 'max_depth':5, 'min_rows':10}
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/setosa_versicolor.csv"))
    test = h2o.upload_file(pyunit_utils.locate("smalldata/iris/virginica.csv"))
    x = [0,1,2,4]
    y = 3

    pyunit_utils.javapredict("random_forest", "numeric", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_smallcat)
else:
    javapredict_smallcat()
