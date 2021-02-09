import sys
import os
import tempfile
import time
import numpy as np

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def _wait_for_grid_models(grid, grid_id, models, grid_size):
    grid_in_progress = None
    times_waited = 0
    while (times_waited < 3000) and (grid_in_progress is None or len(grid_in_progress.model_ids) < models):
        time.sleep(0.1)  # give it tome to train some models
        times_waited += 1
        try:
            grid_in_progress = h2o.get_grid(grid_id)
        except IndexError:
            if times_waited % 100 == 0:
                print("%s not trained yet after %ss" % (models, times_waited / 10))
    grid.cancel()
    grid = h2o.get_grid(grid_id)
    old_grid_model_count = len(grid.model_ids)
    print("Grid has %d models" % old_grid_model_count)
    assert old_grid_model_count < grid_size, "The full grid should not have finished yet."
    h2o.remove_all()
    time.sleep(5)
    return old_grid_model_count


def _check_grid_loaded_properly(loaded, train, old_grid_model_count):
    assert loaded is not None
    assert len(loaded.model_ids) == old_grid_model_count
    loaded_train = h2o.H2OFrame.get_frame(train.frame_id)
    assert loaded_train is not None, "Train frame was not loaded"


def test_resume_with_recovery():
    export_dir = tempfile.mkdtemp()
    grid_id = "resume_with_recovery_gbm"
    print("Using directory %s" % export_dir)
    hyper_parameters = {
        "learn_rate": [0.01, 0.05],
        "ntrees": [100, 110, 120, 130]
    }
    grid_size = 1
    for p in hyper_parameters:
        grid_size *= len(hyper_parameters[p])
    print("Grid size %d" % grid_size)
    print("Starting baseline grid")

    df = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    df["Angaus"] = df["Angaus"].asfactor()
    df["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(df.nrow, 1)).tolist())[0]
    train, calib = df.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)
    params = {
        "distribution": "bernoulli", "min_rows": 10, "max_depth": 5,
        "weights_column": "Weights",
        "calibrate_model": True, 
        "calibration_frame": calib
    }
    recovery_dir_1 = export_dir + "/recovery_1"
    grid = H2OGridSearch(
        H2OGradientBoostingEstimator,
        grid_id=grid_id,
        hyper_params=hyper_parameters,
        recovery_dir=recovery_dir_1
    )
    grid.start(x=list(range(2, train.ncol)), y="Angaus", training_frame=train, **params)
    grid_1_model_count = _wait_for_grid_models(grid, grid_id, 1, grid_size)

    loaded = h2o.load_grid("%s/%s" % (recovery_dir_1, grid_id), load_params_references=True)
    _check_grid_loaded_properly(loaded, train, grid_1_model_count)
    print("Resuming grid")
    recovery_dir_2 = export_dir + "/recovery_2"
    loaded.resume(detach=True, recovery_dir=recovery_dir_2)
    grid_2_model_count = _wait_for_grid_models(loaded, grid_id, len(loaded.model_ids) + 1, grid_size)

    loaded_2 = h2o.load_grid("%s/%s" % (recovery_dir_2, grid_id), load_params_references=True)
    _check_grid_loaded_properly(loaded_2, train, grid_2_model_count)
    print("Resuming grid to finish")
    loaded_2.resume()
    print("Finished grid has %d models" % len(loaded_2.model_ids))
    assert grid_size == len(loaded_2.model_ids)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_resume_with_recovery)
else:
    test_resume_with_recovery()
