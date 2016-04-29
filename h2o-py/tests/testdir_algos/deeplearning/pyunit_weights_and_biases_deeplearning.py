from __future__ import print_function
from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

def weights_and_biases():


  print("Test checks if Deep Learning weights and biases are accessible from R")

  covtype = h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
  covtype[54] = covtype[54].asfactor()


  dlmodel = H2ODeepLearningEstimator(hidden=[17,191],
                                     epochs=1,
                                     balance_classes=False,
                                     reproducible=True,
                                     seed=1234,
                                     export_weights_and_biases=True)
  dlmodel.train(x=list(range(54)),y=54,training_frame=covtype)
  print(dlmodel)

  weights1 = dlmodel.weights(0)
  weights2 = dlmodel.weights(1)
  weights3 = dlmodel.weights(2)

  biases1 = dlmodel.biases(0)
  biases2 = dlmodel.biases(1)
  biases3 = dlmodel.biases(2)

  w1c = weights1.ncol
  w1r = weights1.nrow
  assert w1c == 52, "wrong dimensionality! expected {0}, but got {1}.".format(52, w1c)
  assert w1r == 17, "wrong dimensionality! expected {0}, but got {1}.".format(17, w1r)

  w2c = weights2.ncol
  w2r = weights2.nrow
  assert w2c == 17, "wrong dimensionality! expected {0}, but got {1}.".format(17, w2c)
  assert w2r == 191, "wrong dimensionality! expected {0}, but got {1}.".format(191, w2r)

  w3c = weights3.ncol
  w3r = weights3.nrow
  assert w3c == 191, "wrong dimensionality! expected {0}, but got {1}.".format(191, w3c)
  assert w3r == 7, "wrong dimensionality! expected {0}, but got {1}.".format(7, w3r)

  b1c = biases1.ncol
  b1r = biases1.nrow
  assert b1c == 1, "wrong dimensionality! expected {0}, but got {1}.".format(1, b1c)
  assert b1r == 17, "wrong dimensionality! expected {0}, but got {1}.".format(17, b1r)

  b2c = biases2.ncol
  b2r = biases2.nrow
  assert b2c == 1, "wrong dimensionality! expected {0}, but got {1}.".format(1, b2c)
  assert b2r == 191, "wrong dimensionality! expected {0}, but got {1}.".format(191, b2r)

  b3c = biases3.ncol
  b3r = biases3.nrow
  assert b3c == 1, "wrong dimensionality! expected {0}, but got {1}.".format(1, b3c)
  assert b3r == 7, "wrong dimensionality! expected {0}, but got {1}.".format(7, b3r)


  df = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
  dl1 = H2ODeepLearningEstimator(hidden=[10,10], export_weights_and_biases=True)
  dl1.train(x=list(range(4)), y=4, training_frame=df)
  p1 = dl1.predict(df)
  ll1 = dl1.model_performance(df).logloss()
  print(ll1)

  ## get weights and biases
  w1 = dl1.weights(0)
  w2 = dl1.weights(1)
  w3 = dl1.weights(2)
  b1 = dl1.biases(0)
  b2 = dl1.biases(1)
  b3 = dl1.biases(2)

  ## make a model from given weights/biases
  dl2 = H2ODeepLearningEstimator(hidden=[10,10], initial_weights=[w1, w2, w3], initial_biases=[b1, b2, b3], epochs=0)
  dl2.train(x=list(range(4)), y=4, training_frame=df)
  p2 = dl2.predict(df)
  ll2 = dl2.model_performance(df).logloss()
  print(ll2)

  # h2o.download_pojo(dl2) ## fully functional pojo

  ## check consistency
  assert abs(p1[:,1:4]-p2[:,1:4]).max() < 1e-6
  assert abs(ll2 - ll1) < 1e-6

  ## make another model with partially set weights/biases
  dl3 = H2ODeepLearningEstimator(hidden=[10,10], initial_weights=[w1, None, w3], initial_biases=[b1, b2, None], epochs=10)
  dl3.train(x=list(range(4)), y=4, training_frame=df)
  ll3 = dl3.model_performance(df).logloss()

  ## make another model with partially set user-modified weights/biases
  dl4 = H2ODeepLearningEstimator(hidden=[10,10], initial_weights=[w1*1.1,w2*0.9,w3.sqrt()], initial_biases=[b1, b2, None], epochs=10)
  dl4.train(x=list(range(4)), y=4, training_frame=df)
  ll4 = dl4.model_performance(df).logloss()

if __name__ == "__main__":
  pyunit_utils.standalone_test(weights_and_biases)
else:
  weights_and_biases()
