import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.exceptions import H2OValueError

# Flag-ON client test for GH-16851: exercises the full user path through the Python client —
# setting remove_offset_effects, offset-free predictions, the dual "unrestricted" metric view in the
# model JSON, and scoring a frame without the offset column.

def gbm_remove_offset_effect():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    fr["offset"] = fr["ID"].cos() * 0.3
    x = ["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]

    ro = H2OGradientBoostingEstimator(distribution="gaussian", ntrees=20, max_depth=4, seed=42,
                                      remove_offset_effects=True)
    ro.train(x=x, y="AGE", training_frame=fr, offset_column="offset")
    plain = H2OGradientBoostingEstimator(distribution="gaussian", ntrees=20, max_depth=4, seed=42)
    plain.train(x=x, y="AGE", training_frame=fr, offset_column="offset")

    zeroed = h2o.deep_copy(fr, "zeroed")
    zeroed["offset"] = zeroed["offset"] * 0

    ro_preds = ro.predict(fr).as_data_frame(use_pandas=True).iloc[:, 0].values
    ro_zeroed = ro.predict(zeroed).as_data_frame(use_pandas=True).iloc[:, 0].values
    plain_zeroed = plain.predict(zeroed).as_data_frame(use_pandas=True).iloc[:, 0].values
    plain_preds = plain.predict(fr).as_data_frame(use_pandas=True).iloc[:, 0].values

    # predictions ignore the offset column and equal the identically-fit plain model on zero offset
    assert np.allclose(ro_preds, ro_zeroed, atol=0, rtol=0), "remove_offset predictions must ignore the offset"
    assert np.allclose(ro_preds, plain_zeroed, atol=1e-8), "must equal plain model scored with zero offset"
    assert np.max(np.abs(plain_preds - ro_preds)) > 1e-6, "offset must matter for the plain model"

    # dual view is visible in the model JSON (ModelOutputSchemaV3 exposure)
    unrestricted = ro._model_json["output"]["training_metrics_unrestricted_model"]
    assert unrestricted is not None, "training_metrics_unrestricted_model missing from model JSON"
    restricted_mse = ro._model_json["output"]["training_metrics"]["MSE"]
    unrestricted_mse = unrestricted["MSE"]
    assert abs(unrestricted_mse - restricted_mse) > 1e-6, "restricted vs unrestricted MSE should differ"
    assert plain._model_json["output"]["training_metrics_unrestricted_model"] is None, \
        "plain model must not carry an unrestricted view"

    # the documented accessor returns the same metrics object, is typed like its restricted twin, and
    # defaults to the training view
    assert ro.unrestricted_model_performance(train=True).mse() == unrestricted_mse
    assert ro.unrestricted_model_performance().mse() == unrestricted_mse
    assert isinstance(ro.unrestricted_model_performance(train=True),
                      type(ro.model_performance(train=True)))
    assert plain.unrestricted_model_performance(train=True) is None, \
        "plain model must not report an unrestricted view"
    # the two views must be distinguishable, and computed over the same rows
    assert unrestricted["nobs"] == ro._model_json["output"]["training_metrics"]["nobs"], \
        "both views must cover the same rows, otherwise comparing them is meaningless"
    # only one of train/valid/xval may be requested
    try:
        ro.unrestricted_model_performance(train=True, valid=True)
        assert False, "expected H2OValueError for more than one of train/valid/xval"
    except H2OValueError:
        pass

    # scoring a frame WITHOUT the offset column works (zero column substituted)
    no_offset = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    no_offset_preds = ro.predict(no_offset).as_data_frame(use_pandas=True).iloc[:, 0].values
    assert np.allclose(ro_preds, no_offset_preds, atol=0, rtol=0), \
        "predictions on a frame without the offset column must match"


if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_remove_offset_effect)
else:
    gbm_remove_offset_effect()
