from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _leaderboard_utils import check_leaderboard, check_model_property
from _automl_utils import import_dataset, get_partitioned_model_names


automl_seed = 42

def test_leaderboard_for_regression():
    print("Check leaderboard for Regression with default sorting")
    ds = import_dataset('regression', split=False)
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_regr_sort",
                    exclude_algos=exclude_algos,
                    max_models=8,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "mean_residual_deviance")


def test_leaderboard_for_regression_with_custom_sorting():
    print("Check leaderboard for Regression sort by rmse")
    ds = import_dataset('regression', split=False)
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_regr_sort",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed,
                    sort_metric="RMSE")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["rmse", "mean_residual_deviance", "mse", "mae", "rmsle"], "rmse")


def test_leaderboard_for_regression_with_custom_sorting_deviance():
    print("Check leaderboard for Regression sort by deviance")
    ds = import_dataset('regression', split=False)
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_regr_deviance",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed,
                    sort_metric="deviance")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "mean_residual_deviance")


def test_AUTO_stopping_metric_with_no_sorting_metric_regression():
    print("Check leaderboard with AUTO stopping metric and no sorting metric for regression")
    ds = import_dataset('regression', split=False)
    exclude_algos = ["DeepLearning", "GLM"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_no_sorting_regression",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_residual_deviance", "rmse", "mse", "mae", "rmsle"], "mean_residual_deviance")
    base = get_partitioned_model_names(aml.leaderboard).base
    first = [m for m in base if 'XGBoost_1' in m]
    others = [m for m in base if m not in first]
    check_model_property(first, 'stopping_metric', True, None) #if stopping_rounds == 0, actual value of stopping_metric is set to None
    check_model_property(others, 'stopping_metric', True, "deviance")


def test_AUTO_stopping_metric_with_custom_sorting_metric_regression():
    print("Check leaderboard with AUTO stopping metric and rmse sorting metric")
    ds = import_dataset('regression', split=False)
    exclude_algos = ["DeepLearning", "GLM"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_custom_sorting",
                    exclude_algos=exclude_algos,
                    max_models=10,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    seed=automl_seed,
                    sort_metric="rmse")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["rmse", "mean_residual_deviance", "mse", "mae", "rmsle"], "rmse")
    base = get_partitioned_model_names(aml.leaderboard).base
    check_model_property(base, 'stopping_metric', True, "RMSE")


pu.run_tests([
    test_leaderboard_for_regression,
    test_leaderboard_for_regression_with_custom_sorting,
    test_leaderboard_for_regression_with_custom_sorting_deviance,
    test_AUTO_stopping_metric_with_no_sorting_metric_regression,
    test_AUTO_stopping_metric_with_custom_sorting_metric_regression,
])
