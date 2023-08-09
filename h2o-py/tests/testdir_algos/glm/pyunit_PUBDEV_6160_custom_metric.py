import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from tests.pyunit_utils import CustomMaeFunc, CustomRmseFunc, CustomLoglossFunc, \
    dataset_prostate, dataset_iris
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# Regression Model fixture
def regression_model(ModelType, custom_metric_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="regression",
                      family="gaussian",
                      score_each_iteration=True,
                      custom_metric_func=custom_metric_func)
    model.train(y="AGE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


# Binomial model fixture
def binomial_model(ModelType, custom_metric_func):
    (ftrain, fvalid, ftest) = dataset_prostate()
    model = ModelType(model_id="binomial",
                      family="binomial",
                      score_each_iteration=True,
                      custom_metric_func=custom_metric_func)
    model.train(y="CAPSULE", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


# Multinomial model fixture
def multinomial_model(ModelType, custom_metric_func):
    (ftrain, fvalid, ftest) = dataset_iris()
    model = ModelType(model_id="multinomial",
                      family="multinomial",
                      score_each_iteration=True,
                      custom_metric_func=custom_metric_func)
    model.train(y="class", x=ftrain.names, training_frame=ftrain, validation_frame=fvalid)
    return model, ftest


def assert_custom_metric(model, metric):
    mm_train = model.model_performance(train=True)
    metric = metric if metric in mm_train._metric_json else metric.lower()
    print(metric, mm_train._metric_json["custom_metric_value"])
    assert mm_train._metric_json["custom_metric_name"].lower() == metric.lower()
    assert mm_train._metric_json["custom_metric_value"] == mm_train._metric_json[metric] 
                                                        


# Custom model metrics fixture
def custom_mae_mm():
    return h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")


def custom_rmse_mm():
    return h2o.upload_custom_metric(CustomRmseFunc, func_name="rmse", func_file="mm_rmse.py")


def custom_logloss_mm():
    return h2o.upload_custom_metric(CustomLoglossFunc, func_name="logloss", func_file="mm_logloss.py")


# Shows that the custom model metric is actually calculated
def test_custom_metric_computation_regression():
    (model, _) = regression_model(H2OGeneralizedLinearEstimator, custom_mae_mm())
    assert_custom_metric(model, "MAE")

def test_custom_metric_computation_binomial():
    (model, _) = binomial_model(H2OGeneralizedLinearEstimator, custom_rmse_mm())
    assert_custom_metric(model, "RMSE")

    (model, _) = binomial_model(H2OGeneralizedLinearEstimator, custom_logloss_mm())
    assert_custom_metric(model, "logloss")

def test_custom_metric_computation_multinomial():
    (model, _) = multinomial_model(H2OGeneralizedLinearEstimator, custom_rmse_mm())
    assert_custom_metric(model, "RMSE")


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
