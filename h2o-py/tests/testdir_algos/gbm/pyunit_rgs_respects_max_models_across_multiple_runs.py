from __future__ import print_function
import h2o
import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils

def rgs_respects_max_models_across_multiple_runs():
      data = h2o.import_file(path=pyunit_utils.locate("smalldata/higgs/higgs_train_10k.csv"))
      test = h2o.import_file(path=pyunit_utils.locate("smalldata/higgs/higgs_test_5k.csv"))

      # Identify predictors and response
      x = data.columns
      y = "response"
      x.remove(y)

      # For binary classification, response should be a factor
      data[y] = data[y].asfactor()
      test[y] = test[y].asfactor()

      # Split data into train & validation
      ss = data.split_frame(seed = 1)
      train = ss[0]
      valid = ss[1]

      # GBM hyperparameters
      gbm_params1 = {'learn_rate': [0.01, 0.1],
                     'max_depth': [3, 5, 9],
                     'sample_rate': [0.8, 1.0],
                     'col_sample_rate': [0.2, 0.5, 1.0]}

      search_criteria = {'strategy': 'RandomDiscrete', 'max_models': 2, 'seed': 1}

      gbm_grid1 = H2OGridSearch(model=H2OGradientBoostingEstimator,
                                grid_id='gbm_grid1',
                                hyper_params=gbm_params1, search_criteria=search_criteria)
      gbm_grid1.train(x=x, y=y,
                      training_frame=train,
                      validation_frame=valid,
                      ntrees=100,
                      seed=1)
      gbm_grid1.train(x=x, y=y,
                      training_frame=train,
                      validation_frame=valid,
                      ntrees=100,
                      seed=1)

      assert len(gbm_grid1.models) == 2 , \
        "Failed to respect max_models parameter when running multiple train operations."


if __name__ == "__main__":
  pyunit_utils.standalone_test(rgs_respects_max_models_across_multiple_runs)
else:
  rgs_respects_max_models_across_multiple_runs()