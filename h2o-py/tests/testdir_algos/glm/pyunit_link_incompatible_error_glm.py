import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def link_incompatible_error():
  print("Reading in original prostate data.")
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))

  print("Throw error when trying to create model with incompatible logit link.")
  try:
    model = H2OGeneralizedLinearEstimator( family="gaussian", link="logit")
    model.train(x=range(1,8),y=8, training_frame=prostate)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  try:
    model = H2OGeneralizedLinearEstimator(family="tweedie", link="log")
    model.train(x=range(1,8), y=8, training_frame=prostate)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  try:
    model = H2OGeneralizedLinearEstimator(family="binomial", link="inverse")
    model.train(x=range(2,9), y=1, training_frame=prostate)
    assert False, "expected an error"
  except EnvironmentError:
    assert True


if __name__ == "__main__":
  pyunit_utils.standalone_test(link_incompatible_error)
else:
  link_incompatible_error()
