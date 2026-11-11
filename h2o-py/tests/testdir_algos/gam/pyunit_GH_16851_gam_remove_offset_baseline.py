import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained GAM that does NOT use remove_offset_effects.
# GAM fits through GLM, so the offset enters at the link scale and the oracle is identical to GLM/GBM:
#   link(predict(withOffset)) - link(predict(offsetZeroed)) == offset   exactly.

def _g(p, link):
    if link == "log":
        return np.log(p)
    if link == "logit":
        return np.log(p / (1 - p))
    return p

def gam_remove_offset_baseline():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    fr["offset"] = fr["ID"].cos() * 0.3          # deterministic, row-aligned, small (keeps exp() sane)

    configs = [
        ("gaussian",  "AGE",     "identity", 0),
        ("poisson",   "AGE",     "log",      0),
        # gamma omitted: with lambda=0 + a spline term the IRLSM line search fails to converge (NaN);
        # gamma+offset is already covered by the GLM/GBM/XGBoost baselines.
        ("tweedie",   "AGE",     "log",      0),
        ("binomial",  "CAPSULE", "logit",    2),   # positive-class prob is prediction col 2
    ]
    # Linear predictors; PSA is the smooth term (gam_columns) and is NOT included in x.
    x = ["RACE", "DPROS", "DCAPS", "VOL", "GLEASON"]
    offset = fr["offset"].as_data_frame(use_pandas=False, header=False)
    offset = np.array([float(r[0]) for r in offset])

    for family, y, link, col in configs:
        train = fr
        if link == "logit":
            train = h2o.deep_copy(fr, "train_" + family)
            train["CAPSULE"] = train["CAPSULE"].asfactor()
        params = dict(family=family, gam_columns=["PSA"], num_knots=[5], lambda_=[0], solver="irlsm")
        if family == "tweedie":
            params["tweedie_variance_power"] = 1.5
            params["tweedie_link_power"] = 0
        m = H2OGeneralizedAdditiveEstimator(**params)
        m.train(x=x, y=y, training_frame=train, offset_column="offset")

        preds_a = m.predict(train).as_data_frame(use_pandas=True).iloc[:, col].values
        preds_a2 = m.predict(train).as_data_frame(use_pandas=True).iloc[:, col].values
        assert np.allclose(preds_a, preds_a2, atol=0, rtol=0), family + ": default scoring not deterministic"

        zeroed = h2o.deep_copy(train, "zeroed_" + family)
        zeroed["offset"] = zeroed["offset"] * 0
        preds_zero = m.predict(zeroed).as_data_frame(use_pandas=True).iloc[:, col].values

        assert np.max(np.abs(preds_a - preds_zero)) > 1e-6, family + ": default scoring must apply the offset"
        err = np.max(np.abs((_g(preds_a, link) - _g(preds_zero, link)) - offset))
        assert err < 1e-6, "{0}: link(predWith)-link(predZero) must equal offset, err={1}".format(family, err)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gam_remove_offset_baseline)
else:
    gam_remove_offset_baseline()
