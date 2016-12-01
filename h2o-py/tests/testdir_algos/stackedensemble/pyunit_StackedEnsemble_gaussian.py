from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.stackedensemble import H2OStackedensembleEstimator

def stackedensemble_gaussian():
  australia_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/extdata/australia.csv"), destination_frame="australia.hex")
#  australia_hex = h2o.import_file(path="smalldata/extdata/australia.csv", destination_frame="australia.hex")
  myX = ["premax", "salmax","minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs"]
  # dependent <- "runoffnew"

  my_gbm = H2OGradientBoostingEstimator(ntrees = 10, max_depth = 3, min_rows = 2, learn_rate = 0.2, nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True, distribution = "gaussian")
  my_gbm.train(y = "runoffnew", x = myX, training_frame = australia_hex)
  print("GBM performance: ")
  my_gbm.model_performance(australia_hex).show()

 
  my_glm = H2OGeneralizedLinearEstimator(family = "gaussian", nfolds = 5, fold_assignment='Modulo', keep_cross_validation_predictions = True)
  my_glm.train(y = "runoffnew", x = myX, training_frame = australia_hex)
  print("GLM performance: ")
  my_glm.model_performance(australia_hex).show()


  stacker = H2OStackedensembleEstimator(selection_strategy="choose_all", base_models=[my_gbm.model_id, my_glm.model_id])
  stacker.train(model_id="my_ensemble", x=myX, y="runoffnew", training_frame=australia_hex)
  predictions = stacker.predict(australia_hex)  # training data
  print("preditions for ensemble are in: " + predictions.frame_id)

if __name__ == "__main__":
  pyunit_utils.standalone_test(stackedensemble_gaussian)
else:
  stackedensemble_gaussian()
  

