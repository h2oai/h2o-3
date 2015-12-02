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
  hyper_parameters = {"ntrees": ntrees_opts, "learn_rate": learn_rate_opts}
  print "GBM grid with the following hyper_parameters:", hyper_parameters

  gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
  gs.train(x=range(4), y=4, training_frame=train)
  print("\nsorted by mse: ")
  print gs.sort_by("mse")
  #print gs.hit_ratio_table()

  assert len(gs) == size_of_hyper_space
  total_grid_space = map(list, itertools.product(*hyper_parameters.values()))
  for model in gs.models:
    combo = [model.parms['learn_rate']['actual_value']] + [model.parms['ntrees']['actual_value']]
    assert combo in total_grid_space
    total_grid_space.remove(combo)

  # test back-end sorting of model metrics:
  locally_sorted = gs.sort_by("r2", H2OGridSearch.DESC)
  remotely_sorted_desc = H2OGridSearch.get_grid(H2OGradientBoostingEstimator(distribution='multinomial'), hyper_parameters, gs.grid_id, sort_by='r2', sort_order='desc')

  assert len(locally_sorted.cell_values) == len(remotely_sorted_desc.model_ids), "Expected locally sorted and remotely sorted grids to have the same number of models"
  for i in range(len(remotely_sorted_desc.model_ids)):
    assert locally_sorted.cell_values[i][0] == remotely_sorted_desc.model_ids[i], "Expected back-end sort by r2 to be the same as locally-sorted: " + str(i)

  remotely_sorted_asc = H2OGridSearch.get_grid(H2OGradientBoostingEstimator(distribution='multinomial'), hyper_parameters, gs.grid_id, sort_by='r2', sort_order='asc')

  assert len(locally_sorted.cell_values) == len(remotely_sorted_asc.model_ids), "Expected locally sorted and remotely sorted grids to have the same number of models"
  length = len(remotely_sorted_asc.model_ids)
  for i in range(length):
    assert locally_sorted.cell_values[i][0] == remotely_sorted_asc.model_ids[length - i - 1], "Expected back-end sort by r2, ascending, to be the reverse as locally-sorted ascending: " + str(i)


if __name__ == "__main__":
  pyunit_utils.standalone_test(iris_gbm_grid)
else:
  iris_gbm_grid()
