from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



# PUBDEV-2055
def javapredict_smallcat():

    # optional parameters
    params = {'epochs':100}
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    train = h2o.upload_file(pyunit_utils.locate("smalldata/iris/setosa_versicolor.csv"))
    test = h2o.upload_file(pyunit_utils.locate("smalldata/iris/virginica.csv"))
    x = [0,1,2,4]
    y = 3

    pyunit_utils.javapredict("deeplearning", "numeric", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_smallcat)
else:
    javapredict_smallcat()
