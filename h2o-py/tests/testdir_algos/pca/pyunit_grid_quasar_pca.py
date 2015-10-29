import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random
import copy
from h2o.transforms.decomposition import H2OPCA
from h2o.grid.grid_search import H2OGridSearch

def grid_quasar_pca():

    quasar = h2o.import_file(path=pyunit_utils.locate("smalldata/pca_test/SDSS_quasar.txt.zip"), header=1)
    grid_space = pyunit_utils.make_random_grid_space(algo="pca", ncols=quasar.ncol, nrows=quasar.nrow)
    print "Grid space: {0}".format(grid_space)

    print "Constructing the grid of PCA models..."
    quasar_pca_grid = H2OGridSearch(H2OPCA, hyper_params=grid_space)
    quasar_pca_grid.train(x=range(1,23), training_frame=quasar)
    print "Performing various checks of the constructed grid..."

    print "Check cardinality of grid, that is, the correct number of models have been created..."
    size_of_grid_space = 1
    for v in grid_space.values():
        v2 = [v] if type(v) != list else v
        size_of_grid_space = size_of_grid_space * len(v2)
    actual_size = len(quasar_pca_grid)
    assert size_of_grid_space ==  actual_size, "Expected size of grid to be {0}, but got {1}" \
                                               "".format(size_of_grid_space,actual_size)

    print "Duplicate-entries-in-grid-space check"
    new_grid_space = copy.deepcopy(grid_space)
    for name in grid_space.keys():
        new_grid_space[name] = grid_space[name] + grid_space[name]
    print "The new search space: {0}".format(new_grid_space)
    print "Constructing the new grid of nb models..."
    quasar_pca_grid2 = H2OGridSearch(H2OPCA, hyper_params=new_grid_space)
    quasar_pca_grid2.train(x=range(1,23), training_frame=quasar)
    actual_size2 = len(quasar_pca_grid2)
    assert actual_size == actual_size2, "Expected duplicates to be ignored. Without dups grid size: {0}. With dups " \
                                        "size: {1}".format(actual_size, actual_size2)

    print "Check that the hyper_params that were passed to grid, were used to construct the models..."
    for name in grid_space.keys():
        print name
        pyunit_utils.expect_model_param(quasar_pca_grid, name, grid_space[name])

if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_quasar_pca)
else:
    grid_quasar_pca()