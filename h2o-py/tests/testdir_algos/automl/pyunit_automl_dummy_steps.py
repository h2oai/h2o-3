from __future__ import print_function
import os
import sys
import time

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML

"""This test suite checks the AutoML dummy modeling steps"""


def import_dataset(seed=0, larger=False):
    df = h2o.import_file(path=pu.locate("smalldata/prostate/{}".format("prostate_complete.csv.zip" if larger else "prostate.csv")))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    #Split frames
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    #Set up train, validation, and test sets
    return pu.ns(train=fr[0], valid=fr[1], test=fr[2], target=target, target_idx=1)


# PUBDEV-7288
def test_modeling_plan_dummy():
    sleep = 5  # sleep 5 secs
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_dummy_sleep",
                    max_models=2,
                    modeling_plan=[
                        dict(name='GLM', steps=[dict(id='dummy_sleep', weight=sleep * 10)])
                    ],
                    seed=1)
    start = time.time()
    aml.train(y=ds.target, training_frame=ds.train)
    end = time.time()
    assert sleep < end - start < 3 * sleep


def test_modeling_plan_dummies_exist():
    modeling_plan = [
        dict(name='DeepLearning', steps=[dict(id='dummy_sleep', weight=10)]),  # sleep 1 secs
        dict(name='DRF', steps=[dict(id='dummy_sleep', weight=10)]),
        dict(name='GBM', steps=[dict(id='dummy_sleep', weight=10)]),
        dict(name='GLM', steps=[dict(id='dummy_sleep', weight=10)]),
        dict(name='StackedEnsemble', steps=[dict(id='dummy_sleep', weight=10)]),
        dict(name='XGBoost', steps=[dict(id='dummy_sleep', weight=10)]),
    ]
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_dummy_sleep",
                    max_models=len(modeling_plan),
                    modeling_plan=modeling_plan,
                    seed=1)

    start = time.time()
    aml.train(y=ds.target, training_frame=ds.train)
    end = time.time()
    assert len(modeling_plan) < end - start < 3 * len(modeling_plan)


pu.run_tests([
    test_modeling_plan_dummy,
    test_modeling_plan_dummies_exist,
])
