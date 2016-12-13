from __future__ import print_function
from builtins import map
from builtins import str
from builtins import range
from collections import OrderedDict
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def iris_gbm_grid():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # Run GBM

  ntrees_opts = [1,3]
  learn_rate_opts = [0.1,0.01,.05]
  size_of_hyper_space = len(ntrees_opts) * len(learn_rate_opts)
  hyper_parameters = OrderedDict()
  hyper_parameters["learn_rate"] = learn_rate_opts
  hyper_parameters["ntrees"] = ntrees_opts
  print("GBM grid with the following hyper_parameters:", hyper_parameters)

  gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
  gs.train(x=list(range(4)), y=4, training_frame=train)
  print("\nsorted by mse: ")
  print(gs.get_grid(sort_by="mse"))
  #print gs.hit_ratio_table()

  for model in gs:
    assert isinstance(model, H2OGradientBoostingEstimator)

  assert len(gs) == size_of_hyper_space
  total_grid_space = list(map(list, itertools.product(*list(hyper_parameters.values()))))
  print( str(total_grid_space) )
  for model in gs.models:
    combo = [model.parms['learn_rate']['actual_value'], model.parms['ntrees']['actual_value']]
    assert combo in total_grid_space, "combo: " + str(combo) + "; total_grid_space=" + str(total_grid_space)
    total_grid_space.remove(combo)

if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_gbm_grid)
else:
  iris_gbm_grid()
