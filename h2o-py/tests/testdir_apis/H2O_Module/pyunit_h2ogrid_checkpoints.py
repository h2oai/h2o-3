import sys
import tempfile
import shutil
from os import listdir
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o

from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.utils.typechecks import assert_is_type
from h2o.grid.grid_search import H2OGridSearch


def h2ogrid_checkpoints():
    """
    Python API test: H2OGridSearch with export_checkpoints_dir

    Copy from pyunit_gbm_random_grid.py
    """
    air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="air.hex")
    myX = ["DayofMonth", "DayOfWeek"]

    hyper_parameters = {
        'ntrees': [5, 10]
    }

    search_crit = {'strategy': "RandomDiscrete",
                   'max_models': 5,
                   'seed': 1234,
                   'stopping_rounds' : 2,
                   'stopping_metric' : "AUTO",
                   'stopping_tolerance': 1e-2
                   }
    checkpoints_dir = tempfile.mkdtemp()

    air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
    air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli",
                   learn_rate=0.1,
                   max_depth=3,
                   nfolds=3,
                   export_checkpoints_dir=checkpoints_dir)

    checkpoint_files = listdir(checkpoints_dir) 
    print(checkpoint_files)
    num_files = len(checkpoint_files)
    shutil.rmtree(checkpoints_dir)

    assert_is_type(air_grid, H2OGridSearch)
    assert num_files == 1 + (2 * (1 + 3)), "Unexpected number of checkpoint files" # 1 grid + 1 main model + 3 CV models for each model built
    assert all(model in checkpoint_files for model in air_grid.get_grid().model_ids), \
        "Some models do not have corresponding checkpoints"


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2ogrid_checkpoints)
else:
    h2ogrid_checkpoints()
