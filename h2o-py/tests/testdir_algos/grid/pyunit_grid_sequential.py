import sys
import os
import tempfile

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def test_grid_sequential():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    # run GBM Grid Search
    hyper_parameters_1 = {
        "ntrees": [i for i in range(1, 11)],
        "learn_rate": [10.0**(-i) for i in range(1, 11)]
    }

    grid1 = H2OGridSearch(
        H2OGradientBoostingEstimator,
        hyper_params=hyper_parameters_1,
        search_criteria=dict(
            strategy="Sequential",
            early_stopping=False,
            stopping_tolerance=1e5,
            stopping_rounds=2
        )
    )
    grid1.train(x=list(range(4)), y=4, training_frame=train, seed=1)
    assert len(grid1.model_ids) == 10

    grid2 = H2OGridSearch(
        H2OGradientBoostingEstimator,
        hyper_params=hyper_parameters_1,
        search_criteria=dict(
            strategy="Sequential",
            early_stopping=True,
            stopping_tolerance=1e5,
            stopping_rounds=2
        )
    )
    grid2.train(x=list(range(4)), y=4, training_frame=train, seed=1)
    assert len(grid2.model_ids) == 5

    grid3 = H2OGridSearch(
        H2OGradientBoostingEstimator,
        hyper_params=hyper_parameters_1,
        search_criteria=dict(
            strategy="Sequential",
            early_stopping=False,
            max_models=3
        )
    )
    grid3.train(x=list(range(4)), y=4, training_frame=train, seed=1)
    assert len(grid3.model_ids) == 3

    hyper_parameters_2 = {
        "ntrees": [i for i in range(1, 10001)],
        "learn_rate": [10.0**(-i) for i in range(1, 10001)]
    }

    grid4 = H2OGridSearch(
        H2OGradientBoostingEstimator,
        hyper_params=hyper_parameters_2,
        search_criteria=dict(
            strategy="Sequential",
            early_stopping=False,
            max_runtime_secs=1
        )
    )
    grid4.train(x=list(range(4)), y=4, training_frame=train, seed=1)
    assert len(grid4.model_ids) < 1000


pyunit_utils.standalone_test(test_grid_sequential)
