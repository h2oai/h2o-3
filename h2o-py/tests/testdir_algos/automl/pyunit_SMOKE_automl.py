from __future__ import print_function

import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.automl import H2OAutoML, get_leaderboard
from h2o.model import ModelBase


def test_smoke_automl():
    fr = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()
    nmodels = 20  # enough models to run every step (all base models, all grids, all SEs…)
    
    aml = H2OAutoML(max_models=nmodels, project_name="py_aml_smoke", seed=42, verbosity='debug')
    model = aml.train(y=target, training_frame=fr)
    
    assert isinstance(model, ModelBase)
    lb = get_leaderboard(aml, ['algos', 'provider', 'step', 'group'])
    print(lb)
    assert lb.nrows > nmodels


pu.run_tests([
    test_smoke_automl
])
