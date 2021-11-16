from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_default_automl_with_multiclass_task():
    ds = import_dataset('multiclass')
    aml = H2OAutoML(max_models=2,
                    project_name='aml_multiclass')

    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds.valid, leaderboard_frame=ds.test)
    print(aml.leader)
    print(aml.leaderboard)
    assert aml.leaderboard.columns == ["model_id", "mean_per_class_error", "logloss", "rmse", "mse"]


pu.run_tests([
    test_default_automl_with_multiclass_task
])
