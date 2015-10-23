import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def czechboardRF():




  # Training set has checkerboard pattern
  board = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/czechboard_300x300.csv"))
  board["C3"] = board["C3"].asfactor()
  board.summary()

  # Train H2O DRF Model:
  from h2o.estimators.random_forest import H2ORandomForestEstimator

  model = H2ORandomForestEstimator(ntrees=50, max_depth=20, nbins=500)
  model.train(x=["C1", "C2"], y="C3",training_frame=board)
  model.show()



if __name__ == "__main__":
  pyunit_utils.standalone_test(czechboardRF)
else:
  czechboardRF()
