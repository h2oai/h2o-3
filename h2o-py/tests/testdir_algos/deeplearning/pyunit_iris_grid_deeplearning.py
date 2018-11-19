from __future__ import print_function
from builtins import map
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from collections import OrderedDict

def iris_dl_grid():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # Run DL

  hidden_opts = [[20,20],[50,50,50]]
  loss_opts = ["Quadratic","CrossEntropy"]
  size_of_hyper_space = len(hidden_opts) * len(loss_opts)
  hyper_parameters = OrderedDict()
  hyper_parameters["loss"]  = loss_opts
  hyper_parameters["hidden"] = hidden_opts
  print("DL grid with the following hyper_parameters:", hyper_parameters)

  gs = H2OGridSearch(H2ODeepLearningEstimator, hyper_params=hyper_parameters, grid_id="mygrid")
  gs.train(x=list(range(4)), y=4, training_frame=train)
  print(gs.get_grid(sort_by="mse"))

  for model in gs:
    assert isinstance(model, H2ODeepLearningEstimator)

  assert len(gs) == size_of_hyper_space
  total_grid_space = list(map(list, itertools.product(*list(hyper_parameters.values()))))
  for model in gs.models:
    combo = [model.parms['loss']['actual_value']] + [model.parms['hidden']['actual_value']]
    assert combo in total_grid_space
    total_grid_space.remove(combo)

  print("Check correct type value....")
  model_type = gs[0].type
  true_model_type = "classifier"
  assert model_type == true_model_type, "Type of model ({0}) is incorrect, expected value is {1}.".format(model_type, true_model_type)


if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_dl_grid)
else:
  iris_dl_grid()
