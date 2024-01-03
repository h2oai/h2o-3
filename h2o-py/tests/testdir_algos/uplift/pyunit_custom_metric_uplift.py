import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomAteFunc, CustomAttFunc, CustomAtcFunc, \
    uplift_binomial_model, assert_correct_custom_metric
from h2o.estimators.uplift_random_forest import H2OUpliftRandomForestEstimator


# Custom model metrics fixture
def custom_ate_mm():
    return h2o.upload_custom_metric(CustomAteFunc, func_name="Custom ATE", func_file="mm_ate.py")


def custom_att_mm():
    return h2o.upload_custom_metric(CustomAttFunc, func_name="Custom ATT", func_file="mm_att.py")


def custom_atc_mm():
    return h2o.upload_custom_metric(CustomAtcFunc, func_name="Custom ATC", func_file="mm_atc.py")


# Test that the custom model metric is computed
# and compare them with implicit custom metric
def test_custom_metric_computation_binomial_ate():
    (model, f_test) = uplift_binomial_model(H2OUpliftRandomForestEstimator, custom_ate_mm())
    print(model)
    assert_correct_custom_metric(model, f_test, "ate", "Binomial ATE on prostate")


def test_custom_metric_computation_binomial_att():
    (model, f_test) = uplift_binomial_model(H2OUpliftRandomForestEstimator, custom_att_mm())
    print(model)
    assert_correct_custom_metric(model, f_test, "att", "Binomial ATT on prostate")


def test_custom_metric_computation_binomial_atc():
    (model, f_test) = uplift_binomial_model(H2OUpliftRandomForestEstimator, custom_atc_mm())
    print(model)
    assert_correct_custom_metric(model, f_test, "atc", "Binomial ATC on prostate")


# Tests to invoke in this suite
__TESTS__ = [
    test_custom_metric_computation_binomial_ate,
    test_custom_metric_computation_binomial_att,
    test_custom_metric_computation_binomial_atc
]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
