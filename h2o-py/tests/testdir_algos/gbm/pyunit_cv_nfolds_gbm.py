import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def cv_nfolds_gbm():
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate[1] = prostate[1].asfactor()
  prostate.summary()

  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  prostate_gbm = H2OGradientBoostingEstimator(nfolds=5, distribution="bernoulli")
  prostate_gbm.train(x=range(2,9), y=1, training_frame=prostate)
  prostate_gbm.show()

  # Can specify both nfolds >= 2 and validation data at once
  try:
    H2OGradientBoostingEstimator(nfolds=5,
                                 distribution="bernoulli").train(x=range(2,9),
                                                                 y=1,
                                                                 training_frame=prostate,
                                                                 validation_frame=prostate)

    assert True
  except EnvironmentError:
    assert False, "expected an error"


if __name__ == "__main__":
  pyunit_utils.standalone_test(cv_nfolds_gbm)
else:
  cv_nfolds_gbm()