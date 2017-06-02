from __future__ import print_function
from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# Used to test out customer dataset to make sure h2o model predict and pojo predict generate
# the same answers.  However, customer dataset is not to be made public and hence this test
# is a NOPASS.
def javapredict_gbm_hexdev_692():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/Training_Data.csv"))
    test = h2o.upload_file(pyunit_utils.locate("smalldata/Training_Data.csv"))
    params = {'ntrees':100, 'max_depth':7,  'seed':42, 'training_frame':train } # 651MB pojo
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    x = list(range(0,14))
    y = "Label"

    pyunit_utils.javapredict("gbm", "class", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_gbm_hexdev_692)
else:
    javapredict_gbm_hexdev_692()
