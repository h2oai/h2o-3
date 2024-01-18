import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from collections import OrderedDict
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests.pyunit_utils import CustomMaeFunc, CustomRmseRegressionFunc, CustomNegativeRmseRegressionFunc


def custom_mae_mm():
    return h2o.upload_custom_metric(CustomMaeFunc, func_name="mae", func_file="mm_mae.py")


def custom_nrmse_mm():
    return h2o.upload_custom_metric(CustomNegativeRmseRegressionFunc, func_name="nrmse", func_file="mm_nrmse.py")


def grid_custom_metric():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    # Run GBM Grid Search using Cross Validation with parallelization enabled
    ntrees_opts = [1, 3, 5, 10]
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = ntrees_opts
    hyper_parameters["stopping_metric"] = "custom"
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(H2OGradientBoostingEstimator(custom_metric_func=custom_mae_mm()),
                       hyper_params=hyper_parameters,
                       parallelism=1)
    gs.train(y=3, training_frame=train, nfolds=3)

    assert len(gs.models) == 4
    print(gs.get_grid(sort_by="rmse"))

    print(gs.get_grid(sort_by="mae"))

    # Should be ok - just one definition of custom metric
    print(gs.get_grid(sort_by="custom"))


def grid_custom_increasing_metric():
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    # Run GBM Grid Search using Cross Validation with parallelization enabled
    ntrees_opts = [1, 3, 5, 10]
    hyper_parameters = OrderedDict()
    hyper_parameters["ntrees"] = ntrees_opts
    hyper_parameters["stopping_metric"] = "custom_increasing"
    print("GBM grid with the following hyper_parameters:", hyper_parameters)

    gs = H2OGridSearch(H2OGradientBoostingEstimator(custom_metric_func=custom_nrmse_mm()),
                       hyper_params=hyper_parameters,
                       parallelism=1)
    gs.train(y=3, training_frame=train, nfolds=3)

    assert len(gs.models) == 4
    print(gs.get_grid(sort_by="rmse"))

    print(gs.get_grid(sort_by="mae"))

    # Should be ok - just one definition of custom metric
    print(gs.get_grid(sort_by="custom_increasing", decreasing=True))


if __name__ == "__main__":
    pyunit_utils.run_tests([grid_custom_metric, grid_custom_increasing_metric])
else:
    grid_custom_metric()
    grid_custom_increasing_metric()
