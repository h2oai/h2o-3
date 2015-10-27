import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import os

import random

def milsong_checkpoint():

  milsong_train = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/milsongs/milsongs-train.csv.gz"))
  milsong_valid = h2o.upload_file(pyunit_utils.locate("bigdata/laptop/milsongs/milsongs-test.csv.gz"))

  from h2o.estimators.random_forest import H2ORandomForestEstimator

  # build first model
  ntrees1 = random.sample(range(50,100),1)[0]
  max_depth1 = random.sample(range(2,6),1)[0]
  min_rows1 = random.sample(range(10,16),1)[0]
  print "ntrees model 1: {0}".format(ntrees1)
  print "max_depth model 1: {0}".format(max_depth1)
  print "min_rows model 1: {0}".format(min_rows1)
  model1 = H2ORandomForestEstimator(ntrees=ntrees1,max_depth=max_depth1, min_rows=min_rows1, seed=1234)

  model1.train(x=range(1,milsong_train.ncol), y=0, training_frame=milsong_train, validation_frame=milsong_valid)

  # save the model, then load the model
  path = pyunit_utils.locate("results")

  assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not.".format(path)
  model_path = h2o.save_model(model1, path=path, force=True)

  assert os.path.isdir(model_path), "Expected load directory {0} to exist, but it does not.".format(model_path)
  restored_model = h2o.load_model(model_path)

  # continue building the model
  ntrees2 = ntrees1 + 50
  max_depth2 = max_depth1
  min_rows2 = min_rows1
  print "ntrees model 2: {0}".format(ntrees2)
  print "max_depth model 2: {0}".format(max_depth2)
  print "min_rows model 2: {0}".format(min_rows2)

  model2 = H2ORandomForestEstimator(ntrees=ntrees2,max_depth=max_depth2, min_rows=min_rows2,
                             checkpoint=restored_model._id,seed=1234)
  model2.train(x=range(1,milsong_train.ncol), y=0, training_frame=milsong_train, validation_frame=milsong_valid)

  # build the equivalent of model 2 in one shot
  model3 = H2ORandomForestEstimator(ntrees=ntrees2,max_depth=max_depth2, min_rows=min_rows2, seed=1234)
  model3.train(x=range(1,milsong_train.ncol), y=0, training_frame=milsong_train, validation_frame=milsong_valid)

  assert isinstance(model2,type(model3))
  assert model2.mse(valid=True)==model3.mse(valid=True), "Expected Model 2 MSE: {0} to be the same as Model 4 MSE: {1}".format(model2.mse(valid=True), model3.mse(valid=True))



if __name__ == "__main__":
  pyunit_utils.standalone_test(milsong_checkpoint)
else:
  milsong_checkpoint()
