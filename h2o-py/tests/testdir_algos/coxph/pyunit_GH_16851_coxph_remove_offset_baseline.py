import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
from tests import pyunit_utils
from h2o.estimators.coxph import H2OCoxProportionalHazardsEstimator

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained CoxPH model that does NOT use
# remove_offset_effects. CoxPH's prediction is a clean linear predictor ("lp") and the offset
# enters the linear predictor with coefficient 1.0, so the exact oracle holds:
#   lp(withOffset) - lp(offsetZeroed) == offset.
# We also assert default scoring is deterministic and that the offset genuinely moves predictions.


def coxph_remove_offset_baseline():
    heart = h2o.import_file(pyunit_utils.locate("smalldata/coxph_test/heart.csv"))
    heart["offset"] = heart["id"].cos() * 0.3          # deterministic, row-aligned, small
    offset = heart["offset"].as_data_frame(use_pandas=False, header=False)
    offset = np.array([float(r[0]) for r in offset])

    m = H2OCoxProportionalHazardsEstimator(start_column="start", stop_column="stop",
                                           offset_column="offset", ties="efron")
    m.train(x=["age"], y="event", training_frame=heart)

    preds_a = m.predict(heart).as_data_frame(use_pandas=True)["lp"].values
    preds_a2 = m.predict(heart).as_data_frame(use_pandas=True)["lp"].values
    assert np.allclose(preds_a, preds_a2, atol=0, rtol=0), "default scoring not deterministic"

    zeroed = h2o.deep_copy(heart, "zeroed")
    zeroed["offset"] = zeroed["offset"] * 0
    preds_zero = m.predict(zeroed).as_data_frame(use_pandas=True)["lp"].values

    assert np.max(np.abs(preds_a - preds_zero)) > 1e-6, "default scoring must apply the offset"
    err = np.max(np.abs((preds_a - preds_zero) - offset))
    assert err < 1e-6, "lp(withOffset) - lp(offsetZeroed) must equal offset, err={0}".format(err)


if __name__ == "__main__":
    pyunit_utils.standalone_test(coxph_remove_offset_baseline)
else:
    coxph_remove_offset_baseline()
