import sys
import os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def grid_resume():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    # Run GBM Grid Search
    ntrees_opts = [1, 3]
    learn_rate_opts = [0.1, .05]
    hyper_parameters = {
        "learn_rate": learn_rate_opts,
        "ntrees": ntrees_opts
    }
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    export_dir = pyunit_utils.locate("results") + "/grid_resume_new_hyperspace_1"
    gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
    gs.train(x=list(range(4)), y=4, training_frame=train)
    grid_id = gs.grid_id
    old_grid_model_count = len(gs.model_ids)
    print("Baseline grid has %d models" % old_grid_model_count)
    saved_path = h2o.save_grid(export_dir, grid_id)
    h2o.remove_all()

    # reload everything and restart grid
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    grid = h2o.load_grid(saved_path)
    assert grid is not None
    assert len(grid.model_ids) == old_grid_model_count
    # Modify the hyperspace - should add new models to the grid
    hyper_parameters["ntrees"] = [2, 5]
    grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, grid_id=grid.grid_id)
    grid.train(x=list(range(4)), y=4, training_frame=train)
    print("Newly grained grid has %d models" % len(grid.model_ids))
    assert len(grid.model_ids) == 2 * old_grid_model_count
    
    for model_id in grid.model_ids:
        model = h2o.get_model(model_id)
        assert model is not None

    export_dir2 = pyunit_utils.locate("results") + "/grid_resume_new_hyperspace_2"
    saved_path2 = h2o.save_grid(export_dir2, grid_id, save_params_references=True)
    h2o.remove_all()
    grid = h2o.load_grid(saved_path2, load_params_references=True)
    hyper_parameters["ntrees"] = [6]
    grid.hyper_params = hyper_parameters
    grid.train(x=list(range(4)), y=4, training_frame=train)
    print("Newly grained grid has %d models" % len(grid.model_ids))
    assert len(grid.model_ids) == (2 * old_grid_model_count)+2


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_resume)
else:
    grid_resume()
