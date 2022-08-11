import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import numpy as np


def test_isotonic_regression_uses_clipping_to_calibrate_probabilities():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]

    train, calib_all = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)

    m0 = H2OGradientBoostingEstimator(
        ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
        weights_column="Weights", seed=42
    )
    m0.train(
        x=list(range(2, train.ncol)),
        y="Angaus", training_frame=train
    )

    predict_on_calib_call = m0.predict(calib_all)
    calib_extreme = calib_all[predict_on_calib_call["p1"] > predict_on_calib_call["p1"].median()[0]]

    model = H2OGradientBoostingEstimator(
        ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
        weights_column="Weights", seed=42,
        calibrate_model=True, calibration_frame=calib_extreme, calibration_method="IsotonicRegression"
    )
    model.train(
        x=list(range(2, train.ncol)),
        y="Angaus", training_frame=train
    )

    preds_train = model.predict(train)

    # Check that calibrated probabilities were appended to the output frame
    assert preds_train.col_names == ["predict", "p0", "p1", "cal_p0", "cal_p1"]

    missing_cal_p1 = preds_train.get_summary()["cal_p1"]["missing_count"]
    assert missing_cal_p1 == 0


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_isotonic_regression_uses_clipping_to_calibrate_probabilities)
else:
    test_isotonic_regression_uses_clipping_to_calibrate_probabilities()
