from __future__ import print_function
import os
import sys

from pandas.util.testing import assert_frame_equal

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML, get_automl, get_leaderboard
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset


all_algos = ["DeepLearning", "DRF", "GBM", "GLM", "XGBoost", "StackedEnsemble"]


def test_custom_leaderboard():
    print("Check custom leaderboard")
    ds = import_dataset('binary')
    aml = H2OAutoML(project_name="py_aml_custom_lb_test",
                    max_models=5,
                    seed=42)
    aml.train(y=ds.target, training_frame=ds.train)
    std_columns = ["model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"]
    assert aml.leaderboard.names == std_columns
    assert get_leaderboard(aml).names == std_columns
    assert get_leaderboard(aml, extra_columns=[]).names == std_columns
    assert get_leaderboard(aml, extra_columns='ALL').names == std_columns + ["training_time_ms", "predict_time_per_row_ms", "algo"]
    assert get_leaderboard(aml, extra_columns="unknown").names == std_columns
    assert get_leaderboard(aml, extra_columns=["training_time_ms"]).names == std_columns + ["training_time_ms"]
    assert get_leaderboard(aml, extra_columns=["predict_time_per_row_ms", "training_time_ms"]).names == std_columns + ["predict_time_per_row_ms", "training_time_ms"]
    assert get_leaderboard(aml, extra_columns=["unknown", "training_time_ms"]).names == std_columns + ["training_time_ms"]
    lb_ext = get_leaderboard(aml, extra_columns='ALL')
    print(lb_ext)
    assert all(lb_ext[:, [c for c in lb_ext.columns if c not in ("model_id", "algo")]].isnumeric()), "metrics and extension columns should all be numeric"
    assert (lb_ext["training_time_ms"].as_data_frame().values >= 0).all()
    assert (lb_ext["predict_time_per_row_ms"].as_data_frame().values > 0).all()
    assert (lb_ext["algo"].as_data_frame().isin(["DRF", "DeepLearning", "GBM",
                                                 "GLM", "StackedEnsemble", "XGBoost"]).all().all())


def test_custom_leaderboard_as_method():
    ds = import_dataset('binary')
    aml = H2OAutoML(project_name="py_aml_custom_lb_method_test",
                    max_models=5,
                    seed=42)
    aml.train(y=ds.target, training_frame=ds.train)
    
    assert_frame_equal(aml.get_leaderboard().as_data_frame(), aml.leaderboard.as_data_frame())
    lb_ext = get_leaderboard(aml, extra_columns='ALL')
    assert_frame_equal(aml.get_leaderboard('ALL').as_data_frame(), lb_ext.as_data_frame())
    
    aml2 = get_automl(aml.project_name)
    assert_frame_equal(aml2.get_leaderboard().as_data_frame(), aml.leaderboard.as_data_frame())
    assert_frame_equal(aml2.get_leaderboard('ALL').as_data_frame(), lb_ext.as_data_frame())


pu.run_tests([
    test_custom_leaderboard,
    test_custom_leaderboard_as_method,
])
