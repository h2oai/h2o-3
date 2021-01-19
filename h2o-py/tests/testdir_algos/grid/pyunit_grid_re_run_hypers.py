import sys
import numpy as np
sys.path.insert(1,"../../../")

import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


#  this test tests submission of grid hyper-params with string/enum param values
#  this is mainly na issue on py2 where unicode strings may leak out of hyperparams
#  parsed from back-end
def grid_re_run_hyper_serialization():
    train_data = np.dot(np.random.rand(1000, 10), np.random.rand(10, 100))
    train = h2o.H2OFrame(train_data.tolist(), destination_frame="glrm_train")
    params = {
        "k": 2,
        "init": "User",
        "loss": "Quadratic",
        "regularization_x": "OneSparse",
        "regularization_y": "NonNegative"
    }
    hyper_params = {
        "transform": ["NONE", "STANDARDIZE"],
        "gamma_x": [0.1],
    }

    # train grid
    grid = H2OGridSearch(
        H2OGeneralizedLowRankEstimator,
        hyper_params=hyper_params
    )
    grid.train(x=train.names, training_frame=train, **params)
    print(grid)
    assert len(grid.model_ids) == 2

    # load from back-end and train again
    grid = h2o.get_grid(grid.grid_id)
    grid.hyper_params["gamma_x"] = [0.1, 1]
    grid.train(x=train.names, training_frame=train, **params)
    print(grid)
    assert len(grid.model_ids) == 4


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_re_run_hyper_serialization)
else:
    grid_re_run_hyper_serialization()
