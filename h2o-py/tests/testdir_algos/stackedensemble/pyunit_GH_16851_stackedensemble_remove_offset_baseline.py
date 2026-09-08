import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator

# Phase 0 baseline for GH-16851 (remove_offset_effect for all algos).
# Pins the CURRENT default behavior of an offset-trained StackedEnsemble that does NOT use
# remove_offset_effects. StackedEnsemble has no single-family link oracle (base models + metalearner),
# so we use the simpler baseline oracle: default scoring is deterministic and the offset genuinely
# changes predictions.


def stackedensemble_remove_offset_baseline():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    fr["offset"] = fr["ID"].cos() * 0.3          # deterministic, row-aligned, small
    x = ["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]

    configs = [
        ("gaussian",  "AGE",     0),
        ("bernoulli", "CAPSULE", 2),   # positive-class prob is prediction col 2
    ]

    for family, y, col in configs:
        train = fr
        if family == "bernoulli":
            train = h2o.deep_copy(fr, "train_" + family)
            train["CAPSULE"] = train["CAPSULE"].asfactor()

        base = []
        for seed in (42, 7):
            g = H2OGradientBoostingEstimator(distribution=family, ntrees=10, max_depth=3, seed=seed,
                                             nfolds=3, fold_assignment="Modulo",
                                             keep_cross_validation_predictions=True,
                                             offset_column="offset")
            g.train(x=x, y=y, training_frame=train)
            base.append(g.model_id)

        se = H2OStackedEnsembleEstimator(base_models=base, offset_column="offset", seed=42)
        se.train(x=x, y=y, training_frame=train)

        preds_a = se.predict(train).as_data_frame(use_pandas=True).iloc[:, col].values
        preds_a2 = se.predict(train).as_data_frame(use_pandas=True).iloc[:, col].values
        assert np.allclose(preds_a, preds_a2, atol=0, rtol=0), family + ": default scoring not deterministic"

        zeroed = h2o.deep_copy(train, "zeroed_" + family)
        zeroed["offset"] = zeroed["offset"] * 0
        preds_zero = se.predict(zeroed).as_data_frame(use_pandas=True).iloc[:, col].values

        assert np.max(np.abs(preds_a - preds_zero)) > 1e-6, family + ": default scoring must apply the offset"


if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_remove_offset_baseline)
else:
    stackedensemble_remove_offset_baseline()
