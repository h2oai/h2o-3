import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch


def iris_gbm_grid():
  train = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

  # Run GBM
  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  ntrees_opts = [1,3]
  learn_rate_opts = [0.1,0.01,.05]
  size_of_hyper_space = len(ntrees_opts) * len(learn_rate_opts)
  hyper_parameters = {"ntrees": ntrees_opts, "learn_rate": learn_rate_opts}
  print "GBM grid with the following hyper_parameters:", hyper_parameters

  gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
  gs.train(x=range(4), y=4, training_frame=train)
  print gs.sort_by("mse")
  #print gs.hit_ratio_table()

  assert len(gs) == size_of_hyper_space
  total_grid_space = map(list, itertools.product(*hyper_parameters.values()))
  for model in gs.models:
    combo = [model.parms['learn_rate']['actual_value']] + [model.parms['ntrees']['actual_value']]
    assert combo in total_grid_space
    total_grid_space.remove(combo)



if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_gbm_grid)
else:
  iris_gbm_grid()
