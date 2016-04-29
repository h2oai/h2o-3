from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

from tests import pyunit_utils

def benign_grid():
  training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

  Y = 3
  X = [4,5,6,7,8,9,10,11]

  # NOTE: this tests bad parameter value handling; 'a' is not a float:
  hyper_parameters = {'alpha': [0.01,0.3,0.5,'a'], 'lambda': [1e-5,1e-6,1e-7,1e-8]}
  gs = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'), hyper_parameters)
  gs.train(x=X,y=Y, training_frame=training_data)
  for model in gs:
    assert isinstance(model, H2OGeneralizedLinearEstimator)
  gs.show()
  print(gs.sort_by('F1', False))
  best_model_id = gs.sort_by('F1', False)['Model Id'][0]
  best_model = h2o.get_model(best_model_id)
  best_model.predict(training_data)
  gs.predict(training_data)
  print(gs.get_hyperparams(best_model_id))
  print(gs.grid_id)

  assert best_model.params['family']['actual'] == 'binomial'

  # test search_criteria plumbing and max_models
  search_criteria = { 'strategy': "RandomDiscrete", 'max_models': 3 }
  max_models_g = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial'), hyper_parameters, search_criteria=search_criteria)
  max_models_g.train(x=X,y=Y, training_frame=training_data)

  max_models_g.show()
  print(max_models_g.grid_id)
  print(max_models_g.sort_by('F1', False))

  assert len(max_models_g.models) == 3, "expected 3 models, got: {}".format(len(max_models_g.models))
  print(max_models_g.sorted_metric_table())
  print(max_models_g.get_grid("r2"))

  # test search_criteria plumbing and asymptotic stopping
  search_criteria = { 'strategy': "RandomDiscrete", 'seed': 42, 'stopping_metric': "AUTO", 'stopping_tolerance': 0.1, 'stopping_rounds': 2 }
  asymp_g = H2OGridSearch(H2OGeneralizedLinearEstimator(family='binomial', nfolds=5), hyper_parameters, search_criteria=search_criteria)
  asymp_g.train(x=X,y=Y, training_frame=training_data)

  asymp_g.show()
  print(asymp_g.grid_id)
  print(asymp_g.sort_by('F1', False))

  assert len(asymp_g.models) == 5, "expected 5 models, got: {}".format(len(asymp_g.models))

if __name__ == "__main__":
  pyunit_utils.standalone_test(benign_grid)
else:
  benign_grid()
