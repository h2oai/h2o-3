import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch


def iris_dl_grid():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # Run DL
  from h2o.estimators.deeplearning import H2ODeepLearningEstimator
  hidden_opts = [[20,20],[50,50,50]]
  loss_opts = ["Quadratic","CrossEntropy"]
  size_of_hyper_space = len(hidden_opts) * len(loss_opts)
  hyper_parameters = {"hidden": hidden_opts, "loss": loss_opts}
  print "DL grid with the following hyper_parameters:", hyper_parameters

  gs = H2OGridSearch(H2ODeepLearningEstimator, hyper_params=hyper_parameters)
  gs.train(x=range(4), y=4, training_frame=train)
  print gs.sort_by("mse")

  assert len(gs) == size_of_hyper_space
  total_grid_space = map(list, itertools.product(*hyper_parameters.values()))
  for model in gs.models:
    combo = [model.parms['loss']['actual_value']] + [model.parms['hidden']['actual_value']]
    assert combo in total_grid_space
    total_grid_space.remove(combo)



if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_dl_grid)
else:
  iris_dl_grid()
