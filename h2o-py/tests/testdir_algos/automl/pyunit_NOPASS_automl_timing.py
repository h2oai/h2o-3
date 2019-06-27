from __future__ import print_function
import sys, os, time

from h2o.exceptions import H2OTypeError

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.automl import H2OAutoML

"""
Those tests check time constraints on AutoML runs.
"""


def import_dataset(seed=0, larger=False):
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/{}".format("prostate_complete.csv.zip" if larger else "prostate.csv")))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    #Split frames
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    #Set up train, validation, and test sets
    return dict(train=fr[0], valid=fr[1], test=fr[2], target=target, target_idx=1)


def test_automl_stops_after_max_runtime_secs():
    print("Check that automl gets interrupted after `max_runtime_secs`")
    max_runtime_secs = 30
    cancel_tolerance_secs = 5+5   # should work for most cases given current mechanism, +5 due to SE which currently ignore max_runtime_secs
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_aml_max_runtime_secs", seed=1, max_runtime_secs=max_runtime_secs)
    start = time.time()
    aml.train(y=ds['target'], training_frame=ds['train'])
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
        aml.train(y=ds['target'], training_frame=ds['train'])
        models_count[max_runtime_secs_per_model] = len(aml.leaderboard)
        # print(aml.leaderboard)
    # there may be one model difference as reproducibility is not perfectly guaranteed in time-bound runs
    assert abs(models_count[0] - models_count[max_runtime_secs]) <= 1
    assert abs(models_count[0] - models_count[3]) > 1
    # TODO: add assertions about single model timing once 'automl event_log' is available on client side


pyunit_utils.run_tests([
    test_automl_stops_after_max_runtime_secs,
    test_no_model_takes_more_than_max_runtime_secs_per_model,
])
