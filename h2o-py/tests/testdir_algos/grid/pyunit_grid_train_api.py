from __future__ import print_function
import os
import sys

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils as pu
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.grid import H2OGridSearch
from h2o.model import ModelBase


def test_train_returns_the_trained_models():
    fr = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    fr[target] = fr[target].asfactor()
    
    grid = H2OGridSearch(
        H2OGradientBoostingEstimator,
        dict(
            ntrees=[5, 10],
            learn_rate=[0.1, 0.5]
        )
    )
    result = grid.train(y=target, training_frame=fr)
    assert isinstance(result, H2OGridSearch)
    assert result is grid
    result.predict(fr)


pu.run_tests([
    test_train_returns_the_trained_models
])
