import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
import random
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def grid_glrm_iris():
  print "Importing iris_wheader.csv data..."
  irisH2O = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  irisH2O.describe()
  transform_opts = ["NONE", "DEMEAN", "DESCALE", "STANDARDIZE"]
  k_opts = random.sample(range(1,8),3)
  size_of_hyper_space = len(transform_opts) * len(k_opts)
  hyper_parameters = {"transform":transform_opts, "k":k_opts}
  gx = random.uniform(0,1)
  gy = random.uniform(0,1)
  print "H2O GLRM with , gamma_x = " + str(gx) + ", gamma_y = " + str(gy) +\
        ", hyperparameters = " + str(hyper_parameters)

  gs = H2OGridSearch(H2OGeneralizedLowRankEstimator(loss="Quadratic", gamma_x=gx, gamma_y=gy), hyper_params=hyper_parameters)
  gs.train(x=range(4), y=4, training_frame=irisH2O)
  print gs.sort_by("mse")
  #print gs.hit_ratio_table()

  assert len(gs) == size_of_hyper_space
  total_grid_space = map(list, itertools.product(*hyper_parameters.values()))
  for model in gs.models:
      combo = [model.parms['k']['actual_value']] + [model.parms['transform']['actual_value']]
      assert combo in total_grid_space
      total_grid_space.remove(combo)

if __name__ == "__main__":
  pyunit_utils.standalone_test(grid_glrm_iris)
else:
  grid_glrm_iris()
