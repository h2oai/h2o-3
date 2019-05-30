import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomDistributionGaussian, CustomDistributionGaussianWrong
from tests.pyunit_utils import regression_model_distribution, regression_model_default

from h2o.estimators.gbm import H2OGradientBoostingEstimator


def custom_distribution_gaussian():
    return h2o.upload_custom_distribution(CustomDistributionGaussian, func_name="custom_gaussian", func_file="custom_gaussian.py")


def custom_distribution_gaussian_wrong():
    return h2o.upload_custom_distribution(CustomDistributionGaussianWrong, func_name="custom_gaussian2", func_file="custom_gaussian2.py")


# Test a custom distribution is computed correctly
def test_custom_metric_computation_regression():
    (model, f_test) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_gaussian())
    (model2, f_test2) = regression_model_default(H2OGradientBoostingEstimator)
    (model3, f_test3) = regression_model_distribution(H2OGradientBoostingEstimator, custom_distribution_gaussian_wrong())
    assert model.rmse(valid=False) == model2.rmse(valid=False)
    assert model2.rmse(valid=False) != model3.rmse(valid=False)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_custom_metric_computation_regression)
else:
    test_custom_metric_computation_regression()

