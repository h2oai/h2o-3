from __future__ import print_function

import os
import sys
import tempfile

import h2o

sys.path.insert(1, os.path.join("..", "..", ".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_automl_save_load():
    ds = import_dataset()
    saved_automl = tempfile.mktemp()
    try:
        aml = H2OAutoML(max_models=3)
        aml.train(y=ds.target, training_frame=ds.train)
        aml.save_automl(os.path.dirname(saved_automl), os.path.basename(saved_automl))
        training_info = aml.training_info
        leaderboard = aml.leaderboard.as_data_frame(False, False)
        extended_leaderboard = aml.get_leaderboard(["training_time_ms", "algo"]).as_data_frame(False, False)
        h2o.remove_all()

        loaded_aml = h2o.load_automl(saved_automl)
        loaded_training_info = loaded_aml.training_info
        loaded_leaderboard = loaded_aml.leaderboard.as_data_frame(False, False)
        loaded_extended_leaderboard = loaded_aml.get_leaderboard(["training_time_ms", "algo"]).as_data_frame(False,
                                                                                                             False)
        assert not loaded_aml.get_leaderboard("ALL")["predict_time_per_row_ms"].isna().any()
        assert (loaded_aml.get_leaderboard("ALL")["predict_time_per_row_ms"] > 0).all()

        assert training_info == loaded_training_info
        assert leaderboard == loaded_leaderboard
        assert extended_leaderboard == loaded_extended_leaderboard
    finally:
        os.unlink(saved_automl)


def test_automl_download_upload():
    ds = import_dataset()
    saved_automl = tempfile.mktemp()
    try:
        aml = H2OAutoML(max_models=3)
        aml.train(y=ds.target, training_frame=ds.train)
        aml.download_automl(os.path.dirname(saved_automl), os.path.basename(saved_automl))

        training_info = aml.training_info
        leaderboard = aml.leaderboard.as_data_frame(False, False)
        extended_leaderboard = aml.get_leaderboard(["training_time_ms", "algo"]).as_data_frame(False, False)
        h2o.remove_all()

        loaded_aml = h2o.upload_automl(saved_automl)
        loaded_training_info = loaded_aml.training_info
        loaded_leaderboard = loaded_aml.leaderboard.as_data_frame(False, False)
        loaded_extended_leaderboard = loaded_aml.get_leaderboard(["training_time_ms", "algo"]).as_data_frame(False,
                                                                                                             False)

        assert not loaded_aml.get_leaderboard("ALL")["predict_time_per_row_ms"].isna().any()
        assert (loaded_aml.get_leaderboard("ALL")["predict_time_per_row_ms"] > 0).all()

        assert training_info == loaded_training_info
        assert leaderboard == loaded_leaderboard
        assert extended_leaderboard == loaded_extended_leaderboard
    finally:
        os.unlink(saved_automl)


pu.run_tests([
    test_automl_save_load,
    test_automl_download_upload,
])
