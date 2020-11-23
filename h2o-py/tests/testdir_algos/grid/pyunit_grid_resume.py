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
    ntrees_opts = [1,5]
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = ntrees_opts
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    export_dir = pyunit_utils.locate("results")
    gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters,
                       export_checkpoints_dir=export_dir)
    gs.train(x=list(range(4)), y=4, training_frame=train)
    grid_id = gs.grid_id
    old_grid_model_count = len(gs.model_ids)
    print("Baseline grid has %d models" % old_grid_model_count)
    h2o.remove_all()

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    grid = h2o.load_grid(export_dir + "/" + grid_id)
    assert grid is not None
    assert len(grid.model_ids) == old_grid_model_count
    grid.train(x=list(range(4)), y=4, training_frame=train)
    assert len(grid.model_ids) == old_grid_model_count
    print("Newly grained grid has %d models" % len(grid.model_ids))
    
    for model_id in grid.model_ids:
        model = h2o.get_model(model_id)
        assert model is not None


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_resume)
else:
    grid_resume()
