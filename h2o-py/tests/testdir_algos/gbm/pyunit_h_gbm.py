from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def bernoulli_gbm():

  prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
  prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()
  gbm_h2o = H2OGradientBoostingEstimator(seed = 1234, ntrees=100, learn_rate=0.1,
                                         max_depth=5,
                                         min_rows=10,
                                         distribution="bernoulli")
  gbm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
  h = gbm_h2o.h(prostate_train, ['DPROS','DCAPS'])

  #  Prediction has changed since PUBDEV-8768.
  #  Because previous implementation took also CAPSULE into input which is a bug.
  assert_equals(0.03924324673367584, h, delta=1e-5)


if __name__ == "__main__":
  pyunit_utils.standalone_test(bernoulli_gbm)
else:
  bernoulli_gbm()
