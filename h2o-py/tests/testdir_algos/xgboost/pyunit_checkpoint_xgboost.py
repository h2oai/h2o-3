from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import os
import random
from h2o.estimators.xgboost import H2OXGBoostEstimator
from h2o.exceptions import H2OResponseError
 
def xgboost_checkpoint():
  train = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
  valid = h2o.upload_file(pyunit_utils.locate("smalldata/logreg/prostate_test.csv"))
  distribution = "gaussian"
  max_depth = 8
  min_rows = 10
  ntrees1 = 5
  ntrees2 = ntrees1 * 2

  # build first model
  model1 = H2OXGBoostEstimator(
    ntrees=ntrees1, max_depth=max_depth, min_rows=min_rows, distribution=distribution
  )
  model1.train(
    x=list(range(1,train.ncol)), y=0, training_frame=train, validation_frame=valid
  )

  # save the model, then load the model
  results_path = pyunit_utils.locate("results")
  assert os.path.isdir(results_path), "Expected save directory {0} to exist, but it does not.".format(results_path)
  model_path = h2o.save_model(model1, path=results_path, force=True)

  assert os.path.isfile(model_path), "Expected load file {0} to exist, but it does not.".format(model_path)
  restored_model = h2o.load_model(model_path)

  # continue building the model
  model2 = H2OXGBoostEstimator(
    ntrees=ntrees1, max_depth=max_depth, min_rows=min_rows, distribution=distribution,
    checkpoint=restored_model.model_id
  )
  try:
    model2.train(
      y=0, x=list(range(1,train.ncol)), training_frame=train, validation_frame=valid
    )
  except H2OResponseError as e:
    assert "_ntrees: If checkpoint is specified then requested ntrees must be higher than 6" in e.args[0].msg

  model2 = H2OXGBoostEstimator(
      ntrees=ntrees2, max_depth=max_depth, min_rows=min_rows, distribution=distribution,
      checkpoint=restored_model.model_id
  )
  model2.train(
    y=0, x=list(range(1,train.ncol)), training_frame=train, validation_frame=valid
  )  
  assert model2.ntrees == ntrees2
  
  # build the model all at once
  model3 = H2OXGBoostEstimator(
    ntrees=ntrees2, max_depth=max_depth, min_rows=min_rows, distribution=distribution
  )
  model3.train(
    y=0, x=list(range(1,train.ncol)), training_frame=train, validation_frame=valid
  )
  predict2 = model2.predict(valid)
  predict3 = model3.predict(valid)
  
  pyunit_utils.compare_frames_local(predict2, predict3)
  
  # build the model with partial data
  parts = train.split_frame(ratios=[0.5], seed=0)
  train_part1 = parts[0]
  model4 = H2OXGBoostEstimator(
    ntrees=ntrees1, max_depth=max_depth, min_rows=min_rows, distribution=distribution
  )
  model4.train(
    y=0, x=list(range(1,train_part1.ncol)), training_frame=train_part1, validation_frame=valid
  )
  # and continue will all data
  model5 = H2OXGBoostEstimator(
    ntrees=ntrees2, max_depth=max_depth, min_rows=min_rows, distribution=distribution,
    checkpoint=model4.model_id
  )
  model5.train(
    y=0, x=list(range(1,train.ncol)), training_frame=train, validation_frame=valid
  )
  predict5 = model5.predict(valid)
  
  assert not pyunit_utils.compare_frames_local(predict2, predict5, returnResult=True), "Predictions should be different"


if __name__ == "__main__":
  pyunit_utils.standalone_test(xgboost_checkpoint)
else:
  xgboost_checkpoint()
