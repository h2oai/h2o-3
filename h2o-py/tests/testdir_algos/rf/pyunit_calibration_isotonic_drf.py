import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
import numpy as np
from pandas.testing import assert_frame_equal


def calibration_test():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]

    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)

    model = H2ORandomForestEstimator(
        ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
        weights_column="Weights",
        calibrate_model=True, calibration_frame=calib, calibration_method="IsotonicRegression"
    )
    model.train(
        x=list(range(2, train.ncol)),
        y="Angaus", training_frame=train
    )

    preds_train = model.predict(train)

    # Check that calibrated probabilities were appended to the output frame
    assert preds_train.col_names == ["predict", "p0", "p1", "cal_p0", "cal_p1"]

    preds_calib = model.predict(calib)

    isotonic_train = calib[["Angaus", "Weights"]]
    isotonic_train = isotonic_train.cbind(preds_calib["p1"])

    h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
    h2o_iso_reg.train(training_frame=isotonic_train, x="p1", y="Angaus", weights_column="Weights")
    print(h2o_iso_reg)

    calibrated_p1 = h2o_iso_reg.predict(preds_train)
    expected_preds = preds_train[["predict", "p0", "p1"]]
    expected_preds["cal_p0"] = 1 - calibrated_p1
    expected_preds["cal_p1"] = calibrated_p1

    assert_frame_equal(expected_preds.as_data_frame(), preds_train.as_data_frame())

    assert pyunit_utils.test_java_scoring(model, train, preds_train, 1e-8)

    # test MOJO
    mojo = pyunit_utils.download_mojo(model)
    mojo_prediction = h2o.mojo_predict_pandas(dataframe=train.as_data_frame(), predict_calibrated=True, **mojo)
    assert_frame_equal(expected_preds.as_data_frame(), mojo_prediction)


if __name__ == "__main__":
    pyunit_utils.standalone_test(calibration_test)
else:
    calibration_test()
