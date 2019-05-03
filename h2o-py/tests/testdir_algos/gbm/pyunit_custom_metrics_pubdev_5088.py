import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomMaeFunc, CustomRmseFunc, CustomLoglossFunc, \
    assert_correct_custom_metric, regression_model, multinomial_model, binomial_model
from h2o.estimators.gbm import H2OGradientBoostingEstimator


# Custom model metrics fixture
def custom_mae_mm():
    return h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")


def custom_rmse_mm():
    return h2o.upload_custom_metric(CustomRmseFunc, func_name="rmse", func_file="mm_rmse.py")


def custom_logloss_mm():
    return h2o.upload_custom_metric(CustomLoglossFunc, func_name="logloss", func_file="mm_logloss.py")


# Test that the custom model metric is computed
# and compare them with implicit custom metric
def test_custom_metric_computation_regression():
    (model, f_test) = regression_model(H2OGradientBoostingEstimator, custom_mae_mm())
    assert_correct_custom_metric(model, f_test, "mae", "Regression on prostate")


def test_custom_metric_computation_binomial():
    (model, f_test) = binomial_model(H2OGradientBoostingEstimator, custom_rmse_mm())
    assert_correct_custom_metric(model, f_test, "rmse", "Binomial on prostate")


def test_custom_metric_computation_multinomial():
    (model, f_test) = multinomial_model(H2OGradientBoostingEstimator, custom_rmse_mm())
    assert_correct_custom_metric(model, f_test, "rmse", "Multinomial on iris")


def test_custom_metric_computation_logloss():
    (model, f_test) = binomial_model(H2OGradientBoostingEstimator, custom_logloss_mm())
    assert_correct_custom_metric(model, f_test, "logloss", "Logloss on prostate")


# Tests to invoke in this suite
__TESTS__ = [
    test_custom_metric_computation_binomial,
    test_custom_metric_computation_regression,
    test_custom_metric_computation_multinomial,
    test_custom_metric_computation_logloss
]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
