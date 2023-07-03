from builtins import str
from builtins import zip
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator


def glrm_orthonnmf():
  m = 1000
  n = 100
  k = 10

  print("Uploading random uniform matrix with rows = " + str(m) + " and cols = " + str(n))
  Y = np.random.rand(k,n)
  X = np.random.rand(m, k)
  train = np.dot(X,Y)
  train_h2o = h2o.H2OFrame(train.tolist())

  print("Run GLRM with orthogonal non-negative regularization on X, non-negative regularization on Y")
  initial_y = np.random.rand(k,n)
  initial_y_h2o = h2o.H2OFrame(initial_y.tolist())
  glrm_h2o = H2OGeneralizedLowRankEstimator(k=k,
                                            init="User",
                                            user_y=initial_y_h2o,
                                            loss="Quadratic",
                                            regularization_x="OneSparse",
                                            regularization_y="NonNegative",
                                            gamma_x=1,
                                            gamma_y=1)
  glrm_h2o.train(x=train_h2o.names, training_frame=train_h2o)
  glrm_h2o.show()

  print("Check that X and Y matrices are non-negative")
  fit_y = glrm_h2o._model_json['output']['archetypes'].cell_values
  fit_y_np = [[float(s) for s in list(row)[1:]] for row in fit_y]
  fit_y_np = np.array(fit_y_np)
  fit_x = h2o.get_frame(glrm_h2o._model_json['output']['representation_name'])
  fit_x_np = np.array(h2o.as_list(fit_x))
  assert np.all(fit_y_np >= 0), "Y must contain only non-negative elements"
  assert np.all(fit_x_np >= 0), "X must contain only non-negative elements"

  print("Check that columns of X are orthogonal")
  xtx = np.dot(np.transpose(fit_x_np), fit_x_np)
  offdiag = np.extract(1-np.eye(k), xtx)
  assert np.all(offdiag == 0), "All off diagonal elements of X'X must equal zero"

  print("Check final objective function value")
  fit_xy = np.dot(fit_x_np, fit_y_np)
  glrm_obj = glrm_h2o._model_json['output']['objective']
  sse = np.sum(np.square(train.__sub__(fit_xy)))
  assert abs(glrm_obj - sse) < 1e-6, "Final objective was " + str(glrm_obj) + " but should equal " + str(sse)

  print("Impute XY and check error metrics")
  pred_h2o = glrm_h2o.predict(train_h2o)
  pred_np = np.array(h2o.as_list(pred_h2o))
  assert np.allclose(pred_np, fit_xy), "Imputation for numerics with quadratic loss should equal XY product"
  glrm_numerr = glrm_h2o._model_json['output']['training_metrics']._metric_json['numerr']
  glrm_caterr = glrm_h2o._model_json['output']['training_metrics']._metric_json['caterr']
  assert abs(glrm_numerr - glrm_obj) < 1e-3, "Numeric error was " + str(glrm_numerr) + " but should equal final objective " + str(glrm_obj)
  assert glrm_caterr == 0, "Categorical error was " + str(glrm_caterr) + " but should be zero"

  print("Run GLRM with orthogonal non-negative regularization on both X and Y")
  initial_y = np.random.rand(k,n)
  initial_y_h2o = h2o.H2OFrame(initial_y.tolist())

  glrm_h2o = H2OGeneralizedLowRankEstimator(k=k,
                                            init="User",
                                            user_y=initial_y_h2o,
                                            loss="Quadratic",
                                            regularization_x="OneSparse",
                                            regularization_y="OneSparse",
                                            gamma_x=1,
                                            gamma_y=1)
  glrm_h2o.train(x=train_h2o.names, training_frame=train_h2o)
  glrm_h2o.show()

  print("Check that X and Y matrices are non-negative")
  fit_y = glrm_h2o._model_json['output']['archetypes'].cell_values
  fit_y_np = [[float(s) for s in list(row)[1:]] for row in fit_y]
  fit_y_np = np.array(fit_y_np)
  fit_x = h2o.get_frame(glrm_h2o._model_json['output']['representation_name'])
  fit_x_np = np.array(h2o.as_list(fit_x))
  assert np.all(fit_y_np >= 0), "Y must contain only non-negative elements"
  assert np.all(fit_x_np >= 0), "X must contain only non-negative elements"

  print("Check that columns of X are orthogonal")
  xtx = np.dot(np.transpose(fit_x_np), fit_x_np)
  offdiag_x = np.extract(1-np.eye(k), xtx)
  assert np.all(offdiag_x == 0), "All off diagonal elements of X'X must equal zero"

  print("Check that rows of Y are orthogonal")
  yyt = np.dot(fit_y_np, np.transpose(fit_y_np))
  offdiag_y = np.extract(1-np.eye(k), yyt)
  assert np.all(offdiag_y == 0), "All off diagonal elements of YY' must equal zero"

  print("Check final objective function value")
  fit_xy = np.dot(fit_x_np, fit_y_np)
  glrm_obj = glrm_h2o._model_json['output']['objective']
  sse = np.sum(np.square(train.__sub__(fit_xy)))
  assert abs(glrm_obj - sse) < 1e-6, "Final objective was " + str(glrm_obj) + " but should equal " + str(sse)

  print("Impute XY and check error metrics")
  pred_h2o = glrm_h2o.predict(train_h2o)
  pred_np = np.array(h2o.as_list(pred_h2o))
  assert np.allclose(pred_np, fit_xy), "Imputation for numerics with quadratic loss should equal XY product"
  glrm_numerr = glrm_h2o._model_json['output']['training_metrics']._metric_json['numerr']
  glrm_caterr = glrm_h2o._model_json['output']['training_metrics']._metric_json['caterr']
  assert abs(glrm_numerr - glrm_obj) < 1e-3, "Numeric error was " + str(glrm_numerr) + " but should equal final objective " + str(glrm_obj)
  assert glrm_caterr == 0, "Categorical error was " + str(glrm_caterr) + " but should be zero"



if __name__ == "__main__":
  pyunit_utils.standalone_test(glrm_orthonnmf)
else:
  glrm_orthonnmf()
