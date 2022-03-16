from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import math

# GLM getRegularizationPath not work if standardize=false
def test_regularizationPath():
    boston = h2o.import_file(pyunit_utils.locate("smalldata/gbm_test/BostonHousing.csv"))

    predictors = boston.columns[:-1]
    response = "medv"
    boston['chas'] = boston['chas'].asfactor()
    train, valid = boston.split_frame(ratios = [.8])
    boston_glm = H2OGeneralizedLinearEstimator(standardize = False)
    boston_glm.train(x = predictors, y = response, training_frame = train, validation_frame = valid)

    r = boston_glm.getGLMRegularizationPath(boston_glm)
    assert "coefficients_std" not in r
    
if __name__ == "__main__":
  pyunit_utils.standalone_test(test_regularizationPath)
else:
    test_regularizationPath()
