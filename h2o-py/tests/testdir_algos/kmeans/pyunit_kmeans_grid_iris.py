import sys
sys.path.insert(1,"../../../")
import h2o
import copy
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.kmeans import H2OKMeansEstimator

def kmeans_grid_iris():

  iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
  grid_space = pyunit_utils.make_random_grid_space(algo="km")
  print "Grid space: {0}".format(grid_space)
  print "Constructing grid of Kmeans models"
  iris_grid = H2OGridSearch(H2OKMeansEstimator, hyper_params=grid_space)
  iris_grid.train(x=range(4), training_frame=iris_h2o)

  print "Check cardinality of grid, that is, the correct number of models have been created..."
  size_of_grid_space = 1
  for v in grid_space.values():
      size_of_grid_space = size_of_grid_space * len(v)
  actual_size = len(iris_grid)
  assert size_of_grid_space ==  actual_size, "Expected size of grid to be {0}, but got {1}" \
                                             "".format(size_of_grid_space,actual_size)

  print "Duplicate-entries-in-grid-space check"
  new_grid_space = copy.deepcopy(grid_space)
  for name in grid_space.keys():
      new_grid_space[name] = grid_space[name] + grid_space[name]
  print "The new search space: {0}".format(new_grid_space)
  print "Constructing the new grid of glm models..."
  iris_grid2 = H2OGridSearch(H2OKMeansEstimator, hyper_params=new_grid_space)
  iris_grid2.train(x=range(4), training_frame=iris_h2o)
  actual_size2 = len(iris_grid2)
  assert actual_size == actual_size2, "Expected duplicates to be ignored. Without dups grid size: {0}. With dups " \
                                      "size: {1}".format(actual_size, actual_size2)

  print "Check that the hyper_params that were passed to grid, were used to construct the models..."
  for name in grid_space.keys():
      print name
      pyunit_utils.expect_model_param(iris_grid, name, grid_space[name])



if __name__ == "__main__":
  pyunit_utils.standalone_test(kmeans_grid_iris)
else:
  kmeans_grid_iris()
