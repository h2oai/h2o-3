from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
from h2o.automl import H2OAutoML, get_leaderboard
from h2o.model import ModelBase
from tests import pyunit_utils as pu

pu.load_module("_automl_utils", os.path.join(os.path.dirname(__file__)))
from _automl_utils import import_dataset

"""
Some smoke tests that should be executed on all environments/setup (for example in a setup where XGBoost is not available).
"""


def test_smoke_automl():
    nmodels = 20  # enough models to run every step (all base models, all grids, all SEs…)
    
    ds = import_dataset()
    aml = H2OAutoML(max_models=nmodels, project_name="py_aml_smoke", seed=42, verbosity='debug')
    model = aml.train(y=ds.target, training_frame=ds.train)
    
    assert isinstance(model, ModelBase)
    lb = get_leaderboard(aml, ['algos', 'provider', 'step', 'group'])
    print(lb)
    assert lb.nrows > nmodels


pu.run_tests([
    test_smoke_automl
])
