import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def cv_nfolds_gbm_fold_assignment():
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate[1] = prostate[1].asfactor()
  prostate.summary()

  prostate_gbm = H2OGradientBoostingEstimator(nfolds=5, distribution="bernoulli",
                                              keep_cross_validation_models=True,
                                              keep_cross_validation_predictions=True,
                                              keep_cross_validation_fold_assignment=True)
  prostate_gbm.train(x=list(range(2,9)), y=1, training_frame=prostate)
  prostate_gbm.cross_validation_fold_assignment().describe()
  prostate_gbm.cross_validation_holdout_predictions().describe()
  for m in prostate_gbm.cross_validation_predictions(): m.describe()
  for m in prostate_gbm.cross_validation_models(): m.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(cv_nfolds_gbm_fold_assignment)
else:
  cv_nfolds_gbm_fold_assignment()
