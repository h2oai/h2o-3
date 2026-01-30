import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import numpy as np
from h2o.estimators import H2OXGBoostEstimator, H2OIsotonicRegressionEstimator


def assert_frame_equal(a,b):
    for frame in [a,b]:
        print("## Original frame:")
        print(frame)

        print("## Single thread:")
        single_thread_types = frame.as_data_frame().dtypes
        multi_thread_types = frame.as_data_frame(use_multi_thread=True).dtypes
        print("single thread pandas frame types: {0}".format(single_thread_types))
        print("multi thread pandas frame types: {0}".format(multi_thread_types))
        assert sum(single_thread_types==multi_thread_types)==len(single_thread_types), \
            "expected pandas frame types: {0}, actual pandas frame types: {1} and they are not the " \
            "same!!!".format(single_thread_types, multi_thread_types)

def test_tomas_bug():
    df = h2o.import_file(path="http://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/ecology_model.csv")
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]

    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)

    model = H2OXGBoostEstimator(
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

    print("-"*80)

    assert_frame_equal(expected_preds, preds_train)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_tomas_bug)
else:
    test_tomas_bug()
