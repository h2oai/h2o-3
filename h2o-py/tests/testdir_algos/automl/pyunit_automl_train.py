from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML
from h2o.model import ModelBase


def test_train_returns_leader_model():
    fr = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()
    
    aml = H2OAutoML(max_models=3, project_name="py_aml_rain_result", seed=42)
    model = aml.train(y=target, training_frame=fr)
    
    assert isinstance(model, ModelBase)
    assert model.key == aml.leader.key
    model.predict(fr)


pu.run_tests([
    test_train_returns_leader_model
])
