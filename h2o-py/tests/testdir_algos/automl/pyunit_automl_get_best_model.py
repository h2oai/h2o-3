from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.exceptions import H2OValueError
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset


def test_get_best_model_per_family():
    ds = import_dataset('binary')
    aml = H2OAutoML(project_name="py_aml_best_model_per_family_test",
                    max_models=12,
                    seed=42)
    aml.train(y=ds.target, training_frame=ds.train)

    def _check_best_models(model_ids, criterion):
        # test case insensitivity in algo specification
        top_models = [aml.get_best_model(mtype, criterion) for mtype in ["deeplearning", "drf", "gbm", "GLM",
                                                                         "STaCKeDEnsEmblE", "xgboost"]]
        nones = [v is None for v in top_models]
        assert sum(nones) <= 1 and len(nones) >= 6
        seen = set()
        top_model_ids = [m.model_id for m in top_models if m is not None]
        for model_id in model_ids:
            model_type = model_id.split("_")[0]
            if model_type not in seen:
                assert model_id in top_model_ids, "%s not found in top models %s" % (model_id, top_model_ids)
                if model_type in ("DRF", "XRT"):
                    seen.update(["DRF", "XRT"])
                else:
                    seen.add(model_type)
    # Check default criterion
    model_ids = aml.leaderboard.as_data_frame()["model_id"]
    _check_best_models(model_ids, None)

    # Check AUC criterion (the higher the better) and check case insensitivity
    model_ids = aml.leaderboard.sort(by="auc", ascending=False).as_data_frame()["model_id"]
    _check_best_models(model_ids, "AUC")

    # Check it works for custom criterion (MSE)
    model_ids = aml.leaderboard.sort(by="mse").as_data_frame()["model_id"]
    _check_best_models(model_ids, "mse")

    # Check it works for without specifying a model type
    assert aml.get_best_model().model_id == aml.leaderboard[0, "model_id"]

    # Check it works with just criterion
    assert aml.get_best_model(criterion="mse").model_id == aml.leaderboard.sort(by="mse")[0, "model_id"]

    # Check it works with extra_cols
    top_model = h2o.automl.get_leaderboard(aml, extra_columns=["training_time_ms"]).sort(by="training_time_ms")[0, "model_id"]
    assert aml.get_best_model(criterion="training_time_ms").model_id == top_model

    # Check validation works
    try:
        aml.get_best_model(algorithm="GXboost")
        assert False, "Algorithm validation does not work!"
    except H2OValueError:
        pass
    try:
        aml.get_best_model(criterion="lorem_ipsum_dolor_sit_amet")
        assert False, "Criterion validation does not work!"
    except H2OValueError:
        pass


pu.run_tests([
    test_get_best_model_per_family,
])
