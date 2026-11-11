import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained model that does NOT use remove_offset_effects.
# Unlike GLM/GBM, DeepLearning applies the offset in standardized/response space rather than as a clean
# link-scale add, so we pin only that scoring is deterministic and that the offset is applied (predictions
# differ from the offset-zeroed frame). Offset is supported for regression only in DL (no classification).

def deeplearning_remove_offset_baseline():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    fr["offset"] = fr["ID"].cos() * 0.3          # deterministic, row-aligned, small

    configs = [
        ("gaussian",  "AGE", 0),
        ("poisson",   "AGE", 0),
        ("gamma",     "AGE", 0),
        ("tweedie",   "AGE", 0),
    ]
    x = ["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]

    for family, y, col in configs:
        train = fr
        params = dict(distribution=family, hidden=[8, 8], epochs=30, reproducible=True, seed=42)
        if family == "tweedie":
            params["tweedie_power"] = 1.5
        m = H2ODeepLearningEstimator(**params)
        m.train(x=x, y=y, training_frame=train, offset_column="offset")

        preds_a = m.predict(train).as_data_frame(use_pandas=True).iloc[:, col].values
        preds_a2 = m.predict(train).as_data_frame(use_pandas=True).iloc[:, col].values
        assert np.allclose(preds_a, preds_a2, atol=0, rtol=0), family + ": default scoring not deterministic"

        zeroed = h2o.deep_copy(train, "zeroed_" + family)
        zeroed["offset"] = zeroed["offset"] * 0
        preds_zero = m.predict(zeroed).as_data_frame(use_pandas=True).iloc[:, col].values

        assert np.max(np.abs(preds_a - preds_zero)) > 1e-6, family + ": default scoring must apply the offset"


if __name__ == "__main__":
    pyunit_utils.standalone_test(deeplearning_remove_offset_baseline)
else:
    deeplearning_remove_offset_baseline()
