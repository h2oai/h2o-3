import sys, os
import tempfile

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from collections import OrderedDict
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def grid_parallel():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    # Run GBM Grid Search
    ntrees_opts = [1, 5]
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = ntrees_opts
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, parallelism = 1)
    gs.train(x=list(range(4)), y=4, training_frame=train)
    assert gs is not None
    assert len(gs.model_ids) == len(ntrees_opts)


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_parallel)
else:
    grid_parallel()
