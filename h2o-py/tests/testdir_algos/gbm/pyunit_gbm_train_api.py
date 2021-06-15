from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.model import ModelBase


def test_train_returns_the_trained_model():
    fr = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()
    
    gbm = H2OGradientBoostingEstimator(model_id="py_gbm_train_result", seed=42)
    model = gbm.train(y=target, training_frame=fr)
    
    assert isinstance(model, ModelBase)
    assert model is gbm
    model.predict(fr)


pu.run_tests([
    test_train_returns_the_trained_model
])
