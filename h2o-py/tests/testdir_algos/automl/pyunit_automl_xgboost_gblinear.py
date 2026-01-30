import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
import h2o.exceptions
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names

max_models = 5


def _is_gblinear(model_id):
    model = h2o.get_model(model_id)
    return model.actual_params["booster"] == "gblinear"


def test_automl_doesnt_containt_gblinear_by_default():
    ds = import_dataset()
    aml = H2OAutoML(max_models=20,
                    seed=1, include_algos=["xgboost"])
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]:
        assert not _is_gblinear(m[0])

    print("GBLinear model count: {}".format(
        sum((_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]))))

    aml = H2OAutoML(max_runtime_secs=120,
                    seed=1, include_algos=["xgboost"])
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]:
        assert not _is_gblinear(m[0])

    print("GBLinear model count: {}".format(
        sum((_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]))))


def test_automl_containt_gblinear_when_used_modeling_plan():
    ds = import_dataset()
    aml = H2OAutoML(max_models=6,
                    modeling_plan=[dict(name="XGBoost", steps=[dict(id="grid_gblinear"), dict(id="grid_1")])],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    assert any(_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:])
    print("GBLinear model count: {}".format(
        sum((_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]))))

    aml = H2OAutoML(max_models=6,
                    modeling_plan=[("XGBoost", "grids")],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    assert any(_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:])
    print("GBLinear model count: {}".format(
        sum((_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]))))

    aml = H2OAutoML(max_runtime_secs=60,
                    modeling_plan=[
                        ("XGBoost",)
                    ],
                    seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    assert any(_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:])
    print("GBLinear model count: {}".format(
        sum((_is_gblinear(m[0]) for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]))))


pu.run_tests([
    test_automl_doesnt_containt_gblinear_by_default,
    test_automl_containt_gblinear_when_used_modeling_plan,
])
