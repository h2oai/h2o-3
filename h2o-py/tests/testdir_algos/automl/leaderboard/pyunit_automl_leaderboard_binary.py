from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _leaderboard_utils import check_leaderboard, check_model_property
from _automl_utils import import_dataset, get_partitioned_model_names


automl_seed = 42


def test_leaderboard_for_binary():
    print("Check leaderboard for Binomial with default sorting")
    ds = import_dataset('binary', split=False)
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_binom_sort",
                    seed=automl_seed,
                    max_models=8,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"], "auc", True)


def test_leaderboard_for_binary_with_custom_sorting():
    print("Check leaderboard for Binomial sort by logloss")
    ds = import_dataset('binary', split=False)
    exclude_algos = ["GLM", "DRF"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_binom_sort",
                    seed=automl_seed,
                    max_models=8,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    exclude_algos=exclude_algos,
                    sort_metric="logloss")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["logloss", "auc", "aucpr", "mean_per_class_error", "rmse", "mse"], "logloss")


def test_AUTO_stopping_metric_with_no_sorting_metric_binary():
    print("Check leaderboard with AUTO stopping metric and no sorting metric for binary")
    ds = import_dataset('binary', split=False)
    exclude_algos = ["DeepLearning", "GLM", "StackedEnsemble"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_no_sorting_binary",
                    seed=automl_seed,
                    max_models=10,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"], "auc", True)
    base = get_partitioned_model_names(aml.leaderboard).base
    first = [m for m in base if 'XGBoost_1' in m]
    others = [m for m in base if m not in first]
    check_model_property(first, 'stopping_metric', True, None) #if stopping_rounds == 0, actual value of stopping_metric is set to None
    check_model_property(others, 'stopping_metric', True, "logloss")


def test_AUTO_stopping_metric_with_auc_sorting_metric():
    print("Check leaderboard with AUTO stopping metric and auc sorting metric")
    ds = import_dataset('binary', split=False)
    exclude_algos = ["DeepLearning", "GLM", "StackedEnsemble"]
    aml = H2OAutoML(project_name="py_aml_lb_test_auto_stopping_metric_auc_sorting",
                    seed=automl_seed,
                    max_models=10,
                    nfolds=3,
                    stopping_rounds=1,
                    stopping_tolerance=0.5,
                    exclude_algos=exclude_algos,
                    sort_metric='auc')
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"], "auc", True)
    base = get_partitioned_model_names(aml.leaderboard).base
    check_model_property(base, 'stopping_metric', True, "logloss")


pu.run_tests([
    test_leaderboard_for_binary,
    test_leaderboard_for_binary_with_custom_sorting,
    test_AUTO_stopping_metric_with_no_sorting_metric_binary,
    test_AUTO_stopping_metric_with_auc_sorting_metric,
])
