from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _leaderboard_utils import check_leaderboard
from _automl_utils import import_dataset, get_partitioned_model_names


automl_seed = 42


def test_leaderboard_for_multiclass():
    print("Check leaderboard for multiclass with default sorting")
    ds = import_dataset('multiclass', split=False)
    exclude_algos = ["GBM", "DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_default_multiclass_sort",
                    seed=automl_seed,
                    max_models=8,
                    exclude_algos=exclude_algos)
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["mean_per_class_error", "logloss", "rmse", "mse"], "mean_per_class_error")


def test_leaderboard_for_multiclass_with_custom_sorting():
    print("Check leaderboard for multiclass sort by logloss")
    ds = import_dataset('multiclass', split=False)
    exclude_algos = ["DeepLearning"]
    aml = H2OAutoML(project_name="py_aml_lb_test_custom_multiclass_sort",
                    seed=automl_seed,
                    max_models=10,
                    exclude_algos=exclude_algos,
                    sort_metric="logloss")
    aml.train(y=ds.target, training_frame=ds.train)

    check_leaderboard(aml, exclude_algos, ["logloss", "mean_per_class_error", "rmse", "mse"], "logloss")


pu.run_tests([
    test_leaderboard_for_multiclass,
    test_leaderboard_for_multiclass_with_custom_sorting,
])
