import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
import h2o.exceptions
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset

MAX_MODELS = 14  # Minimal amount of models to contain a model from the gblinear grid


def _is_gblinear(model_id):
    model = h2o.get_model(model_id)
    return model.actual_params.get("booster") == "gblinear"


def models_has_same_hyperparams(m1, m2):
    for k, v in m1.params.items():
        if k in ["model_id", "training_frame", "validation_frame", "base_models"]:
            continue
        if k not in m2.params.keys() or v["input"] != m2.params[k]["input"]:
            return False
    return True


def model_is_in_automl(model, automl):
    for m in automl.leaderboard.as_data_frame(use_pandas=False)[1:]:
        mod = h2o.get_model(m[0])
        if models_has_same_hyperparams(model, mod):
            return True
    print(model.model_id)
    return False


def test_automl_XGBoost_gblinear_reproducible_modeling_plan():
    ds = import_dataset()
    aml = H2OAutoML(max_models=MAX_MODELS, seed=1)
    aml.train(y=ds.target, training_frame=ds.train)
    print(aml.leaderboard)
    for m in aml.leaderboard.as_data_frame(use_pandas=False)[1:]:
        assert not _is_gblinear(m[0])

    aml2 = H2OAutoML(max_models=MAX_MODELS, seed=1, modeling_plan=[
        dict(name="XGBoost", steps=[
            dict(id="def_2", group=1, weight=10),
            dict(id="def_1", group=2, weight=10),
            dict(id="def_3", group=3, weight=10),
            dict(id="grid_1", group=4, weight=90),
            dict(id="lr_search", group=7, weight=30),
        ]), dict(name="GLM", steps=[
            dict(id="def_1", group=1, weight=10),
        ]), dict(name="DRF", steps=[
            dict(id="def_1", group=2, weight=10),
            dict(id="XRT", group=3, weight=10),
        ]), dict(name="GBM", steps=[
            dict(id="def_5", group=1, weight=10),
            dict(id="def_2", group=2, weight=10),
            dict(id="def_3", group=2, weight=10),
            dict(id="def_4", group=2, weight=10),
            dict(id="def_1", group=3, weight=10),
            dict(id="grid_1", group=4, weight=60),
            dict(id="lr_annealing", group=7, weight=10),
        ]), dict(name="DeepLearning", steps=[
            dict(id="def_1", group=3, weight=10),
            dict(id="grid_1", group=4, weight=30),
            dict(id="grid_2", group=5, weight=30),
            dict(id="grid_3", group=5, weight=30),
        ]), dict(name="completion", steps=[
            dict(id="resume_best_grids", group=6, weight=60),
        ]), dict(name="StackedEnsemble", steps=[
            dict(id="monotonic", group=9, weight=10),
            dict(id="best_of_family_xglm", group=10, weight=10),
            dict(id="all_xglm", group=10, weight=10),
        ])])
    aml2.train(y=ds.target, training_frame=ds.train)
    print(aml2.leaderboard)
    for m in aml2.leaderboard.as_data_frame(use_pandas=False)[1:]:
        assert model_is_in_automl(h2o.get_model(m[0]), aml)

    aml_with_gblinear = H2OAutoML(max_models=MAX_MODELS, seed=1, modeling_plan=[
        dict(name="XGBoost", steps=[
            dict(id="def_2", group=1, weight=10),
            dict(id="def_1", group=2, weight=10),
            dict(id="def_3", group=3, weight=10),
            dict(id="grid_1", group=4, weight=90),
            dict(id="grid_gblinear", group=4, weight=90),  # << XGBoost GBLinear booster grid
            dict(id="lr_search", group=7, weight=30),
        ]), dict(name="GLM", steps=[
            dict(id="def_1", group=1, weight=10),
        ]), dict(name="DRF", steps=[
            dict(id="def_1", group=2, weight=10),
            dict(id="XRT", group=3, weight=10),
        ]), dict(name="GBM", steps=[
            dict(id="def_5", group=1, weight=10),
            dict(id="def_2", group=2, weight=10),
            dict(id="def_3", group=2, weight=10),
            dict(id="def_4", group=2, weight=10),
            dict(id="def_1", group=3, weight=10),
            dict(id="grid_1", group=4, weight=60),
            dict(id="lr_annealing", group=7, weight=10),
        ]), dict(name="DeepLearning", steps=[
            dict(id="def_1", group=3, weight=10),
            dict(id="grid_1", group=4, weight=30),
            dict(id="grid_2", group=5, weight=30),
            dict(id="grid_3", group=5, weight=30),
        ]), dict(name="completion", steps=[
            dict(id="resume_best_grids", group=6, weight=60),
        ]), dict(name="StackedEnsemble", steps=[
            dict(id="monotonic", group=9, weight=10),
            dict(id="best_of_family_xglm", group=10, weight=10),
            dict(id="all_xglm", group=10, weight=10),
        ])])
    aml_with_gblinear.train(y=ds.target, training_frame=ds.train)
    print(aml_with_gblinear.leaderboard)
    for m in aml_with_gblinear.leaderboard.as_data_frame(use_pandas=False)[1:]:
        assert model_is_in_automl(h2o.get_model(m[0]), aml) or _is_gblinear(m[0]), m[0]

    print("GBLinear model count: {}".format(
        sum((_is_gblinear(m[0]) for m in aml_with_gblinear.leaderboard.as_data_frame(use_pandas=False)[1:]))))

    assert any((_is_gblinear(m[0]) for m in aml_with_gblinear.leaderboard.as_data_frame(use_pandas=False)[1:]))


pu.run_tests([
    test_automl_XGBoost_gblinear_reproducible_modeling_plan,
])
