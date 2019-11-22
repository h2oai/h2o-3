from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.xgboost import H2OXGBoostEstimator

def tree_algos_ntree_actual():
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate[1] = prostate[1].asfactor()
  prostate.summary()
  ntrees_original = 1000

  prostate_gbm = H2OGradientBoostingEstimator(nfolds=5,ntrees=ntrees_original, distribution="bernoulli", stopping_metric="MSE", stopping_tolerance=0.01, stopping_rounds=5)
  prostate_gbm.train(x=list(range(2,9)), y=1, training_frame=prostate)
  
  print("\n")
  print("GradientBoosting: number of trees set by user before building the model is:")
  print(ntrees_original)
  print("GradientBoosting: number of trees built with early-stopping is:")
  print(prostate_gbm.ntrees_actual())

  assert prostate_gbm.ntrees_actual() < ntrees_original
  assert prostate_gbm.ntrees_actual() == prostate_gbm._model_json['output']['model_summary']['number_of_trees'][0] == prostate_gbm.summary()['number_of_trees'][0]


  prostate_if = H2OIsolationForestEstimator(sample_rate = 0.1, max_depth = 20, ntrees=ntrees_original, stopping_metric="anomalyscore", stopping_tolerance=0.01, stopping_rounds=5)
  prostate_if.train(x=list(range(2,9)), y=1, training_frame=prostate)

  print("\n")
  print("IsolationForest: number of trees set by user before building the model is:")
  print(ntrees_original)
  print("IsolationForest: number of trees built with early-stopping is:")
  print(prostate_if.ntrees_actual())

  assert prostate_if.ntrees_actual() < ntrees_original
  assert prostate_if.ntrees_actual() == prostate_if._model_json['output']['model_summary']['number_of_trees'][0] == prostate_if.summary()['number_of_trees'][0]

  prostate_drf = H2ORandomForestEstimator(ntrees=ntrees_original, max_depth=20, min_rows=10, stopping_metric="auc", stopping_tolerance=0.01, stopping_rounds=5)
  prostate_drf.train(x=list(range(2,9)), y=1, training_frame=prostate)

  print("\n")
  print("RandomForest: number of trees set by user before building the model is:")
  print(ntrees_original)
  print("RandomForest: number of trees built with early-stopping is:")
  print(prostate_drf.ntrees_actual())

  assert prostate_drf.ntrees_actual() < ntrees_original
  assert prostate_drf.ntrees_actual() == prostate_drf._model_json['output']['model_summary']['number_of_trees'][0] == prostate_drf.summary()['number_of_trees'][0]

  prostate_xgb = H2OXGBoostEstimator(distribution="auto", ntrees=ntrees_original, seed=1, stopping_metric="auc", stopping_tolerance=0.01, stopping_rounds=5)
  prostate_xgb.train(x=list(range(2,9)), y=1, training_frame=prostate)

  print("\n")
  print("XGBoost: number of trees set by user before building the model is:")
  print(ntrees_original)
  print("XGBoost: number of trees built with early-stopping is:")
  print(prostate_xgb.ntrees_actual())

  assert prostate_xgb.ntrees_actual() < ntrees_original
  assert prostate_xgb.ntrees_actual() == prostate_xgb._model_json['output']['model_summary']['number_of_trees'][0] == prostate_xgb.summary()['number_of_trees'][0]

if __name__ == "__main__":
  pyunit_utils.standalone_test(tree_algos_ntree_actual)
else:
    tree_algos_ntree_actual()
