import sys
import os
import tempfile

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def grid_resume():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    # run GBM Grid Search
    hyper_parameters_1 = {
        "ntrees": [10, 50],
        "learn_rate": [0.01, 0.1]
    }
    grid_size_1 = len(hyper_parameters_1["ntrees"]) * len(hyper_parameters_1["learn_rate"])
    print("Training GBM grid with the following hyper_parameters:", hyper_parameters_1)

    export_dir = tempfile.mkdtemp()
    grid = H2OGridSearch(
        H2OGradientBoostingEstimator,
        hyper_params=hyper_parameters_1,
        export_checkpoints_dir=export_dir
    )
    grid.train(x=list(range(4)), y=4, training_frame=train)
    grid_id = grid.grid_id
    model_count_1 = len(grid.model_ids)
    print(grid)
    assert len(grid.model_ids) == grid_size_1, "There should be %d models" % grid_size_1
    print("Baseline grid has %d models" % model_count_1)
    h2o.remove_all()

    # start over
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    grid = h2o.load_grid(export_dir + "/" + grid_id)
    assert len(grid.model_ids) == model_count_1
    hyper_parameters_2 = {
        "ntrees": [10, 20, 50],
        "learn_rate": [0.01, 0.1]
    }
    grid.hyper_params = hyper_parameters_2
    print("Training GBM grid with the following hyper_parameters:", hyper_parameters_2)
    grid.train(x=list(range(4)), y=4, training_frame=train)
    grid_size_2 = len(hyper_parameters_2["ntrees"]) * len(hyper_parameters_2["learn_rate"])
    print(grid)
    assert len(grid.model_ids) == grid_size_2, "There should be %s models" % grid_size_2
    print("Newly grained grid has %d models" % len(grid.model_ids))


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_resume)
else:
    grid_resume()
