from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML
from h2o.model import ModelBase
from tests import pyunit_utils as pu

from _automl_utils import import_dataset


def test_train_returns_leader_model():
    ds = import_dataset()
    aml = H2OAutoML(max_models=3, project_name="py_aml_rain_result", seed=42)
    model = aml.train(y=ds.target, training_frame=ds.train)
    
    assert isinstance(model, ModelBase)
    assert model.key == aml.leader.key
    model.predict(ds.test)


pu.run_tests([
    test_train_returns_leader_model
])
