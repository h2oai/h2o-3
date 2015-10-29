import sys
from h2o.grid.grid_search import H2OGridSearch

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def benign_grid():
  training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

  Y = 3
  X = range(3) + range(4,11)

  from h2o.estimators.glm import H2OGeneralizedLinearEstimator
  hyper_parameters = {'alpha': [0.01,0.5,'a'], 'lambda': [1e-5,1e-6]}
  gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'), hyper_parameters)
  gs.show()
  gs.train(x=X,y=Y, training_frame=training_data)
  gs.show()
  print gs.sort_by('F1', False)
  best_model_id = gs.sort_by('F1', False)['Model Id'][0]
  best_model = h2o.get_model(best_model_id)
  best_model.predict(training_data)
  gs.predict(training_data)
  print gs.get_hyperparams(best_model_id)

  assert best_model.params['family']['actual'] == 'binomial'

if __name__ == "__main__":
  pyunit_utils.standalone_test(benign_grid)
else:
  benign_grid()
