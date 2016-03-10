import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

from h2o.estimators.gbm import H2OGradientBoostingEstimator


def pubdev_1696():
    

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))

    try:
      H2OGradientBoostingEstimator(nfolds=-99).train(x=[0,1,2],y=3,training_frame=iris)
      assert False, "expected an error"
    except EnvironmentError:
        assert True



if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_1696)
else:
    pubdev_1696()
