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
    ds = import_dataset()
    aml = H2OAutoML(project_name="py_modeling_plan_dummy_sleep",
                    max_models=2,
                    modeling_plan=[
                        dict(name='Dummy', steps=[dict(id='dummy_sleep')])
                    ],
                    max_runtime_secs_per_model=5,
                    seed=1)
    start = time.time()
    aml.train(y=ds.target, training_frame=ds.train)
    end = time.time()
    # Should be little over 5 secs...
    assert 5 < end - start < 20


pu.run_tests([
    test_modeling_plan_dummy,
])
