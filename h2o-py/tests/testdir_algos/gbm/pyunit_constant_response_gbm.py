from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def constant_col_gbm():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    train["constantCol"] = 1

    # Run GBM, which should run successfully with constant response when check_constant_response is set to false
    my_gbm = H2OGradientBoostingEstimator(check_constant_response=False)
    my_gbm.train(x=list(range(1,5)), y="constantCol", training_frame=train)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(constant_col_gbm)
else:
    constant_col_gbm()
