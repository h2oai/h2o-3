import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from collections import OrderedDict
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def grid_resume():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    # Run GBM Grid Search
    ntrees_opts = [1, 3]
    learn_rate_opts = [0.1, 0.01, .05]
    hyper_parameters = OrderedDict()
    hyper_parameters["learn_rate"] = learn_rate_opts
    hyper_parameters["ntrees"] = ntrees_opts
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters,
                       export_checkpoints_dir="/home/pavel/ext")
    gs.train(x=list(range(4)), y=4, training_frame=train)

    grid = h2o.resume_grid_search(grid_directory="/home/pavel/ext/", grid_id=gs.grid_id)
    assert grid is not None
    grid.start(x=list(range(4)), y=4, training_frame=train)


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_resume)
else:
    grid_resume()
