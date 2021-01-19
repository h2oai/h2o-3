import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from collections import OrderedDict
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator


def grid_export_with_cv():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    # Run GBM Grid Search
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = [1, 2]

    # train with CV
    gs = H2OGridSearch(H2OGradientBoostingEstimator(nfolds=2, keep_cross_validation_predictions=True, seed=42),
                       hyper_params=hyper_parameters)
    gs.train(x=list(range(4)), y=4, training_frame=train)

    holdout_frame_ids = map(lambda m: m.cross_validation_holdout_predictions().frame_id, gs.models)

    export_dir = pyunit_utils.locate("results")
    saved_path = h2o.save_grid(export_dir, gs.grid_id, export_cross_validation_predictions=True)

    h2o.remove_all()

    grid = h2o.load_grid(saved_path)

    assert grid is not None
    for holdout_frame_id in holdout_frame_ids:
        assert h2o.get_frame(holdout_frame_id) is not None

    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    stack = H2OStackedEnsembleEstimator(base_models=grid.model_ids)
    stack.train(x=list(range(4)), y=4, training_frame=train)

    predicted = stack.predict(train)
    assert predicted.nrow == train.nrow


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_export_with_cv)
else:
    grid_export_with_cv()
