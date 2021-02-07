from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from timeit import default_timer as timer

    
def cv_nfolds_gbm():
  prostate = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/lending-club/loan.csv"))
  prostate[1] = prostate[1].asfactor()
  prostate.summary()

  prostate_gbm = H2OGradientBoostingEstimator(nfolds=5, distribution="bernoulli", ntrees=500, 
                                              score_tree_interval=3, stopping_rounds=2, seed=42)
  start = timer()
  prostate_gbm.train(x=list(range(2,9)), y=1, training_frame=prostate)
  end = timer()
  print(end - start)
  #prostate_gbm.show()
  # 0.723664045334


if __name__ == "__main__":
  pyunit_utils.standalone_test(cv_nfolds_gbm)
else:
  cv_nfolds_gbm()
