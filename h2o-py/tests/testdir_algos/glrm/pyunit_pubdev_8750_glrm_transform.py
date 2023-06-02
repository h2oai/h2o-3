from builtins import str
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def test_glrm_transform():
  # generate training and test frames
  m = 1000
  n = 100
  k = 8
  np.random.seed(12345)

  print("Uploading random uniform matrix with rows = " + str(m) + " and cols = " + str(n))
  Y = np.random.rand(k,n)
  X = np.random.rand(m, k)
  train = np.dot(X,Y)
  train_h2o = h2o.H2OFrame(train.tolist())
  frames = train_h2o.split_frame(ratios=[0.9])
  train = frames[0]
  test = frames[1]

  glrm_h2o = H2OGeneralizedLowRankEstimator(k=k, loss="Quadratic", seed=12345)
  glrm_h2o.train(x=train_h2o.names, training_frame=train)
  predFrame = glrm_h2o.predict(test)
  xFrame = glrm_h2o.transform_frame(test)

  glrm_h2o2 = H2OGeneralizedLowRankEstimator(k=k, loss="Quadratic", seed=12345)
  glrm_h2o2.train(x=train_h2o.names, training_frame=train)
  xFrame2 = glrm_h2o2.transform_frame(test)
  
  assert predFrame.nrows==xFrame.nrows, "predictor frame number of row: {0}, transform frame number of row: " \
                                              "{1}".format(predFrame.nrows,xFrame.nrows)
  pyunit_utils.compare_frames_local(xFrame, xFrame2, prob=1.0, tol=1e-6)

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_glrm_transform)
else:
  test_glrm_transform()
