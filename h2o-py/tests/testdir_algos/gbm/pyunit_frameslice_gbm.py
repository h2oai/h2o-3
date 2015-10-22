import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def frameslice_gbm():
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate = prostate[1:9]

  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  model = H2OGradientBoostingEstimator()
  model.train(x=range(1,8),y=0, training_frame=prostate)



if __name__ == "__main__":
  pyunit_utils.standalone_test(frameslice_gbm)
else:
  frameslice_gbm()
