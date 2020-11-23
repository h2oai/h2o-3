import sys
import numpy as np
import tempfile
sys.path.insert(1,"../../../")

import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_grid_user_y():
    export_dir = tempfile.mkdtemp()
    train_data = np.dot(np.random.rand(1000, 10), np.random.rand(10, 100))
    train = h2o.H2OFrame(train_data.tolist())
    initial_y_data = np.random.rand(10, 100)
    initial_y_h2o = h2o.H2OFrame(initial_y_data.tolist(), destination_frame="glrm_initial_y")
    params = {
        "k": 10,
        "init": "User",
        "user_y": initial_y_h2o,
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
    grid_path = h2o.save_grid(export_dir, grid.grid_id)
    h2o.remove_all()
    
    # reimport and train some more
    train = h2o.H2OFrame(train_data.tolist())
    initial_y = h2o.H2OFrame(initial_y_data.tolist(), destination_frame="glrm_initial_y")
    hyper_params["gamma_x"] = [0.1, 1]
    grid = h2o.load_grid(grid_path)
    grid.train(x=train.names, training_frame=train, **params)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_grid_user_y)
else:
    glrm_grid_user_y()
