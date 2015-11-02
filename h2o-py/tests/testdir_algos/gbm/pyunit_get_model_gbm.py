import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils


def get_model_gbm():
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate.describe()
  prostate[1] = prostate[1].asfactor()
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  prostate_gbm = H2OGradientBoostingEstimator(distribution="bernoulli")
  prostate_gbm.train(x=range(2,9),y=1, training_frame=prostate)
  prostate_gbm.show()

  prostate_gbm.predict(prostate)
  model = h2o.get_model(prostate_gbm.model_id)
  model.show()


if __name__ == "__main__":
  pyunit_utils.standalone_test(get_model_gbm)
else:
  get_model_gbm()
