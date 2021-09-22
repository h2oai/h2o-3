from __future__ import print_function

import contextlib
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML
from h2o.model import ModelBase


def test_smoke_with_xgboost_disabled():
    fr = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()
    
    aml = H2OAutoML(max_models=3, project_name="py_aml_xgb_disabled", seed=42, verbosity='debug')
    model = aml.train(y=target, training_frame=fr)
    
    assert isinstance(model, ModelBase)
    print(aml.get_leaderboard(['provider', 'step', 'group']))


pu.run_tests([
    test_smoke_with_xgboost_disabled
], init_options=dict(jvm_custom_args=["-Dsys.ai.h2o.ext.core.toggle.XGBoost=false"]))
