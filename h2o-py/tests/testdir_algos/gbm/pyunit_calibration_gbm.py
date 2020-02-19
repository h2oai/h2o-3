import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import numpy as np


def calibration_test():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]
    print(df.col_names)
    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)
    
    model = H2OGradientBoostingEstimator(
        ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
        weights_column="Weights",
        calibrate_model=True, calibration_frame=calib
    )
    model.train(
        x=list(range(2, train.ncol)), 
        y="Angaus", training_frame=train
    )

    preds = model.predict(train)

    # Check that calibrated probabilities were appended to the output frame
    assert preds.col_names == ["predict", "p0", "p1", "cal_p0", "cal_p1"]

    # Manually scale the probabilities using GLM in R
    preds_calib = model.predict(calib)
    manual_calib_input = preds_calib["p1"].cbind(calib[["Angaus", "Weights"]])
    manual_calib_input.col_names = ["p1", "response", "weights"]
    manual_calib_model = H2OGeneralizedLinearEstimator(
        family="binomial", weights_column="weights", lambda_=0, intercept=True
    )
    manual_calib_model.train(y="response", training_frame=manual_calib_input)
    manual_calib_predicted = manual_calib_model.predict(preds["p1"])

    print(preds["cal_p1"])
    print(manual_calib_predicted)

    pyunit_utils.compare_frames_local(preds["cal_p1"], manual_calib_predicted["p1"], prob=1)


if __name__ == "__main__":
  pyunit_utils.standalone_test(calibration_test)
else:
  calibration_test()
