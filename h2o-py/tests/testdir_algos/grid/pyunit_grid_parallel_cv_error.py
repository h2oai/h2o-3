import sys
import os
import random

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def grid_parallel():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    fold_assignments = h2o.H2OFrame([[random.randint(0, 4)] for f in range(train.nrow)])
    fold_assignments.set_names(["fold_assignment"])
    train = train.cbind(fold_assignments)

    hyper_parameters = {
        "ntrees": [1, 3, 5],
        "min_rows": [1, 10, 100]
    }
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(
        H2OGradientBoostingEstimator, 
        hyper_params=hyper_parameters, 
        parallelism=4
    )
    gs.train(x=list(range(4)), y=4, training_frame=train, fold_column="fold_assignment")
    assert gs is not None
    # only six models are trained, since CV is not possible with min_rows=100
    print(gs.model_ids)
    assert len(gs.model_ids) == 6


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_parallel)
else:
    grid_parallel()
