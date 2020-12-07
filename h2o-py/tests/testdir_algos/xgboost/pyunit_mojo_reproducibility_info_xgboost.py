import sys, os

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import numpy as np
from h2o.estimators.xgboost import H2OXGBoostEstimator

def xgb_mojo_reproducibility_info():
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]
    print(df.col_names)
    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)

    model = H2OXGBoostEstimator(
        ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5,
        weights_column="Weights",
        calibrate_model=True, calibration_frame=calib
    )
    model.train(
        x=list(range(2, train.ncol)),
        y="Angaus", training_frame=train
    )

    print("Downloading Java prediction model code from H2O")
    TMPDIR = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results", model._id))
    os.makedirs(TMPDIR)
    mojo_path = model.download_mojo(path=TMPDIR)
    xgbModel = h2o.upload_mojo(mojo_path=mojo_path)

    isinstance(xgbModel._model_json['output']['reproducibility_information_table'][1]['h2o_cluster_uptime'][0], float)
    isinstance(xgbModel._model_json['output']['reproducibility_information_table'][0]['java_version'][0], str)
    assert(xgbModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][0] == 'training_frame')
    assert(xgbModel._model_json['output']['reproducibility_information_table'][2]['input_frame'][2] == 'calibration_frame')

if __name__ == "__main__":
    pyunit_utils.standalone_test(xgb_mojo_reproducibility_info)
else:
    xgb_mojo_reproducibility_info()
