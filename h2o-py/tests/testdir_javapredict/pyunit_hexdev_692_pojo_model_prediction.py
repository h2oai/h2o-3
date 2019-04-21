from __future__ import print_function
from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# Used to test out customer dataset to make sure h2o model predict and pojo predict generate
# the same answers.  The prostate dataset has been changed to null out a column name.  My
# fix should work.
def javapredict_gbm_hexdev_692():
    train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_train_null_column_name.csv"))
    test = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_train_null_column_name.csv"))
    params = {'ntrees':100, 'max_depth':5,  'seed':42, 'training_frame':train,
              'learn_rate':0.1, 'min_rows':10, 'distribution':"bernoulli"} # 651MB pojo
    train["CAPSULE"] = train["CAPSULE"].asfactor()
    test["CAPSULE"] = test["CAPSULE"].asfactor()
    print("Parameter list:")
    for k,v in zip(list(params.keys()), list(params.values())): print("{0}, {1}".format(k,v))

    x = list(range(0,train.ncol))
    y = "CAPSULE"

    pyunit_utils.javapredict("gbm", "class", train, test, x, y, **params)



if __name__ == "__main__":
    pyunit_utils.standalone_test(javapredict_gbm_hexdev_692)
else:
    javapredict_gbm_hexdev_692()
