from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import random
from h2o.estimators import H2OExtendedIsolationForestEstimator
from h2o.grid.grid_search import H2OGridSearch
import h2o


def grid_search_eif():
    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    grid_space = {
        'sample_size': random.sample(list(range(128, 256)), random.randint(2, 3)),
        'extension_level': [0, 1]
    }
    print("Grid space: {0}".format(grid_space))

    print("Constructing the grid of IF models...")
    eif_grid = H2OGridSearch(H2OExtendedIsolationForestEstimator, hyper_params=grid_space)
    eif_grid.train(training_frame=train)

    print("Check correct type value....")
    model_type = eif_grid[0].type
    assert model_type == 'unsupervised', "Type of model ({0}) is incorrect, expected value is 'unsupervised'.".format(model_type)

    print("Performing various checks of the constructed grid...")

    print("Check cardinality of grid, that is, the correct number of models have been created...")
    size_of_grid_space = 1
    for v in list(grid_space.values()):
        size_of_grid_space = size_of_grid_space * len(v)
    actual_size = len(eif_grid)

    print("Expected size of grid space: {0}".format(size_of_grid_space))
    assert size_of_grid_space == actual_size, "Expected size of grid to be {0}, but got {1}".format(size_of_grid_space, actual_size)
    print(eif_grid)


if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_search_eif)
else:
    grid_search_eif()
