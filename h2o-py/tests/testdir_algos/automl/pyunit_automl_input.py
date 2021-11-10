from __future__ import print_function
import sys, os

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset

max_models = 2


def test_invalid_project_name():
    print("Check constructor raises error if project name is invalid")
    try:
        H2OAutoML(project_name="1nvalid")
    except Exception as e:
        assert "H2OAutoML" in str(e)
        assert "1nvalid" in str(e)


def test_no_x_train_set_only():
    print("AutoML run with x not provided and train set only")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml1", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds.target, training_frame=ds.train)
    assert aml.project_name == "py_aml1", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_validation_sets():
    print("AutoML run with x not provided with train and valid")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml2", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds['valid'])
    assert aml.project_name == "py_aml2", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    log_df = aml.event_log.as_data_frame()
    warn_messages = log_df[log_df['level'] == 'Warn']['message']
    assert warn_messages.str.startswith("User specified a validation frame with cross-validation still enabled").any(), \
        "a warning should have been raised for using a validation frame with CV enabled"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_test_sets():
    print("AutoML run with x not provided with train and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml3", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds.target, training_frame=ds.train, leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml3", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_train_and_validation_and_test_sets():
    print("AutoML run with x not provided with train, valid, and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml4", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234, nfolds=0)
    aml.train(y=ds.target, training_frame=ds.train, validation_frame=ds['valid'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml4", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    log_df = aml.event_log.as_data_frame()
    warn_messages = log_df[log_df['level'] == 'Warn']['message']
    assert not warn_messages.str.startswith("User specified a validation frame with cross-validation still enabled").any(), \
        "no warning should have been raised as CV was disabled"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_no_x_y_as_idx_train_and_validation_and_test_sets():
    print("AutoML run with x not provided and y as col idx with train, valid, and test")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml5", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="AUC", max_models=max_models, seed=1234)
    aml.train(y=ds['target_idx'], training_frame=ds.train, validation_frame=ds['valid'], leaderboard_frame=ds['test'])
    assert aml.project_name == "py_aml5", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "AUC", "stopping_metrics is not set to `AUC`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_frames_can_be_passed_as_key():
    print("Check that all AutoML frames can be passed as keys.")
    ds = import_dataset()

    kw_args = [
        dict(training_frame=ds.train.frame_id),
        dict(training_frame=ds.train, validation_frame=ds['valid'].frame_id),
        dict(training_frame=ds.train, blending_frame=ds['valid'].frame_id),
        dict(training_frame=ds.train, leaderboard_frame=ds['test'].frame_id),
    ]
    
    for kwargs in kw_args:
        aml = H2OAutoML(project_name="py_aml_frames_as_keys", seed=1, max_models=1, nfolds=0)
        aml.train(y=ds.target, **kwargs)
        h2o.remove(aml)


pu.run_tests([
    test_invalid_project_name,
    test_no_x_train_set_only,
    test_no_x_train_and_validation_sets,
    test_no_x_train_and_test_sets,
    test_no_x_train_and_validation_and_test_sets,
    test_no_x_y_as_idx_train_and_validation_and_test_sets,
    test_frames_can_be_passed_as_key,
])
