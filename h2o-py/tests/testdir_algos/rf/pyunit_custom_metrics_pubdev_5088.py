import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomMaeFunc, CustomRmseFunc,\
    assert_correct_custom_metric, dataset_prostate, dataset_iris
from h2o.estimators.random_forest import H2ORandomForestEstimator


# Custom model metrics fixture
def custom_mae_mm():
    return h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")


def custom_rmse_mm():
    return h2o.upload_custom_metric(CustomRmseFunc, func_name="rmse", func_file="mm_rmse.py")


# Regression Model fixture
def regression_model(custom_metric_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = H2ORandomForestEstimator(model_id="rf_regression", ntrees=3, max_depth=5,
                                     score_each_iteration=True,
                                     custom_metric_func=custom_metric_func)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


# Binomial model fixture
def binomial_model(custom_metric_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = H2ORandomForestEstimator(model_id="rf_binomial", ntrees=3, max_depth=5,
                                     score_each_iteration=True,
                                     custom_metric_func=custom_metric_func)
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def multinomial_model(custom_metric_func):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = H2ORandomForestEstimator(model_id="rf_multinomial", ntrees=3, max_depth=5,
                                     score_each_iteration=True,
                                     custom_metric_func=custom_metric_func)
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


# Test that the custom model metric is computed
# and compare them with implicit custom metric
def test_custom_metric_computation_regression():
    (model, f_test) = regression_model(custom_mae_mm())
    assert_correct_custom_metric(model, f_test, "mae", "Regression on prostate")


def test_custom_metric_computation_binomial():
    (model, f_test) = binomial_model(custom_rmse_mm())
    assert_correct_custom_metric(model, f_test, "rmse", "Binomial on prostate")
    

def test_custom_metric_computation_multinomial():
    (model, f_test) = multinomial_model(custom_rmse_mm())
    assert_correct_custom_metric(model, f_test, "rmse", "Multinomial on iris")


# Tests to invoke in this suite
__TESTS__ = [
    test_custom_metric_computation_binomial,
    test_custom_metric_computation_regression,
    test_custom_metric_computation_multinomial
]

if __name__ == "__main__":
    for func in __TESTS__:
        pyunit_utils.standalone_test(func)
else:
    for func in __TESTS__:
        func()
