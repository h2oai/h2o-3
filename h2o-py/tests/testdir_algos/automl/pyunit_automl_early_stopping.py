from __future__ import print_function
import sys, os

from h2o.exceptions import H2OJobCancelled

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from tests import pyunit_utils as pu

from _automl_utils import import_dataset, get_partitioned_model_names

max_models = 2


def test_early_stopping_defaults():
    print("Check default early stopping params")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_early_stopping_defaults", max_models=max_models)
    aml.train(y=ds.target, training_frame=ds.train)
    stopping_criteria = aml._build_resp['build_control']['stopping_criteria']
    print(stopping_criteria)

    from math import sqrt
    auto_stopping_tolerance = (lambda fr: min(0.05, max(0.001, 1/sqrt((1 - sum(fr.nacnt()) / (fr.ncols * fr.nrows)) * fr.nrows))))(ds.train)

    assert stopping_criteria['stopping_rounds'] == 3
    assert stopping_criteria['stopping_tolerance'] == auto_stopping_tolerance
    assert stopping_criteria['stopping_metric'] == 'AUTO'
    assert stopping_criteria['max_models'] == max_models
    assert stopping_criteria['max_runtime_secs'] == 0
    assert stopping_criteria['max_runtime_secs_per_model'] == 0


def test_early_stopping_args():
    print("Check arguments to H2OAutoML class")
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml0", stopping_rounds=3, stopping_tolerance=0.001, stopping_metric="auc", max_models=max_models, seed=1234, exclude_algos=["DeepLearning"])
    aml.train(y=ds.target, training_frame=ds.train)
    assert aml.project_name == "py_aml0", "Project name is not set"
    assert aml.stopping_rounds == 3, "stopping_rounds is not set to 3"
    assert aml.stopping_tolerance == 0.001, "stopping_tolerance is not set to 0.001"
    assert aml.stopping_metric == "auc", "stopping_metrics is not set to `auc`"
    assert aml.max_models == 2, "max_models is not set to 2"
    assert aml.seed == 1234, "seed is not set to `1234`"
    print("Check leaderboard")
    print(aml.leaderboard)


def test_automl_stops_after_max_models():
    print("Check that automl gets interrupted after `max_models`")
    ds = import_dataset()
    max_models = 5
    aml = H2OAutoML(project_name="py_aml_max_models", seed=1, max_models=max_models)
    aml.train(y=ds.target, training_frame=ds.train)

    base_models = get_partitioned_model_names(aml.leaderboard).base
    assert len(base_models) == max_models, "obtained {} base models when {} are expected".format(len(base_models), max_models)


def test_no_time_limit_if_max_models_is_provided():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_no_time_limit", seed=1, max_models=1)
    aml.train(y=ds.target, training_frame=ds.train)
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_models == 1, max_models
    assert max_runtime == 0, max_runtime
    
    
def test_max_runtime_secs_alone():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_max_runtime_secs", seed=1, max_runtime_secs=7)
    aml.train(y=ds.target, training_frame=ds.train)
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_runtime == 7
    assert max_models == 0


def test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_models_wins():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_all_stopping_constraints", seed=1, max_models=1, max_runtime_secs=1200)
    aml.train(y=ds.target, training_frame=ds.train)
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_runtime == 1200
    assert max_models == 1
    assert aml.leaderboard.nrows == 1
    assert int(aml.training_info['duration_secs']) < max_runtime/2  # being generous to avoid errors on slow Jenkins


def test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_runtime_wins():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_all_stopping_constraints", seed=1, max_models=100, max_runtime_secs=5)
    aml.train(y=ds.target, training_frame=ds.train)
    max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
    max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
    assert max_runtime == 5
    assert max_models == 100
    assert aml.leaderboard.nrows < 100
    assert int(aml.training_info['duration_secs']) < 2*max_runtime  # being generous to avoid errors on slow Jenkins


def test_default_max_runtime_if_no_max_models_provided():
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_no_stopping_constraints", seed=1, verbosity='Info')
    with pu.Timeout(5, on_timeout=lambda: aml._job.cancel()):
        try:
            aml.train(y=ds.target, training_frame=ds.train)
        except H2OJobCancelled:
            pass
        max_runtime = aml._build_resp['build_control']['stopping_criteria']['max_runtime_secs']
        max_models = aml._build_resp['build_control']['stopping_criteria']['max_models']
        assert max_runtime == 3600
        assert max_models == 0


pu.run_tests([
    test_early_stopping_defaults,
    test_early_stopping_args,
    test_automl_stops_after_max_models,
    test_no_time_limit_if_max_models_is_provided,
    test_max_runtime_secs_alone,
    test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_models_wins,
    test_max_runtime_secs_can_be_set_in_combination_with_max_models_and_max_runtime_wins,
    test_default_max_runtime_if_no_max_models_provided,
])
