from __future__ import print_function
import sys, os, time

sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset

"""
Those tests check time constraints on AutoML runs and can be fragile when run on Jenkins, 
hence the NOPASS prefix that won't fail the build if they don't pass.
"""


def test_automl_stops_after_max_runtime_secs():
    print("Check that automl gets interrupted after `max_runtime_secs`")
    max_runtime_secs = 30
    cancel_tolerance_secs = 5+5   # should work for most cases given current mechanism, +5 due to SE which currently ignore max_runtime_secs
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_max_runtime_secs", seed=1, max_runtime_secs=max_runtime_secs)
    start = time.time()
    aml.train(y=ds.target, training_frame=ds.train)
    end = time.time()
    assert abs(end-start - max_runtime_secs) < cancel_tolerance_secs, end-start


def test_no_model_takes_more_than_max_runtime_secs_per_model():
    print("Check that individual model get interrupted after `max_runtime_secs_per_model`")
    ds = import_dataset(seed=1, larger=True)
    max_runtime_secs = 30
    models_count = {}
    for max_runtime_secs_per_model in [0, 3, max_runtime_secs]:
        aml = H2OAutoML(project_name="py_aml_max_runtime_secs_per_model_{}".format(max_runtime_secs_per_model), seed=1,
                        max_runtime_secs_per_model=max_runtime_secs_per_model,
                        max_runtime_secs=max_runtime_secs)
        aml.train(y=ds.target, training_frame=ds.train)
        models_count[max_runtime_secs_per_model] = len(aml.leaderboard)
        # print(aml.leaderboard)
    # there may be one model difference as reproducibility is not perfectly guaranteed in time-bound runs
    assert abs(models_count[0] - models_count[max_runtime_secs]) <= 1
    assert abs(models_count[0] - models_count[3]) > 1
    # TODO: add assertions about single model timing once 'automl event_log' is available on client side


pu.run_tests([
    test_automl_stops_after_max_runtime_secs,
    test_no_model_takes_more_than_max_runtime_secs_per_model,
])
