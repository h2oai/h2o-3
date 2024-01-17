import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator
from pandas.testing import assert_frame_equal
from h2o.utils.threading import local_context


def test_calibrate_existing_model():
    with local_context(datatable_disabled=True, polars_disabled=True): # conversion h2o frame to pandas using single thread as before
      df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
      df["Angaus"] = df["Angaus"].asfactor()

      train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)

      model_int_calib = H2OGradientBoostingEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
                                                     seed=42, calibrate_model=True, calibration_frame=calib, 
                                                     calibration_method="IsotonicRegression")
      model_int_calib.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)
      preds_int_calib = model_int_calib.predict(train)

      isotonic_train = calib[["Angaus"]]
      isotonic_train = isotonic_train.cbind(model_int_calib.predict(calib)["p1"])
      h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
      h2o_iso_reg.train(training_frame=isotonic_train, x="p1", y="Angaus")

      model_man_calib = H2OGradientBoostingEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
                                                     seed=42)
      model_man_calib.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)
      preds_no_calib = model_man_calib.predict(train)
      assert preds_no_calib.col_names == ["predict", "p0", "p1"]

      model_man_calib.calibrate(h2o_iso_reg)

      preds_man_calib = model_man_calib.predict(train)
      assert preds_man_calib.col_names == ["predict", "p0", "p1", "cal_p0", "cal_p1"]

      assert_frame_equal(preds_int_calib.as_data_frame(), preds_man_calib.as_data_frame())

      # test MOJO
      mojo = pyunit_utils.download_mojo(model_man_calib)
      mojo_prediction = h2o.mojo_predict_pandas(dataframe=train.as_data_frame(), predict_calibrated=True, **mojo)
      assert_frame_equal(preds_int_calib.as_data_frame(), mojo_prediction)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_calibrate_existing_model)
else:
    test_calibrate_existing_model()
