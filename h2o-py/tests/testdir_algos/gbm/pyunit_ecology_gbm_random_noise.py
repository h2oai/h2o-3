from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
from sklearn import ensemble
from sklearn.metrics import roc_auc_score
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def ecologyGBM_random_noise():

  ecology_train = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
  gbm_h2o = H2OGradientBoostingEstimator(pred_noise_bandwidth=0.5)
  gbm_h2o.train(x=list(range(2,ecology_train.ncol)), y="Angaus", training_frame=ecology_train)
  print(gbm_h2o)

if __name__ == "__main__":
  pyunit_utils.standalone_test(ecologyGBM_random_noise)
else:
  ecologyGBM_random_noise()
