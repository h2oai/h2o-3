import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o import H2OFrame
import numpy as np
import scipy.stats
from sklearn import ensemble
from sklearn.metrics import roc_auc_score


def bernoulli_synthetic_data_gbm_medium():

  # Generate training dataset (adaptation of http://www.stat.missouri.edu/~speckman/stat461/boost.R)
  train_rows = 10000
  train_cols = 10

  #  Generate variables V1, ... V10
  X_train = np.random.randn(train_rows, train_cols)

  #  y = +1 if sum_i x_{ij}^2 > chisq median on 10 df
  y_train = np.asarray([1 if rs > scipy.stats.chi2.ppf(0.5, 10) else -1 for rs in [sum(r) for r in
                                                                                   np.multiply(X_train,X_train).tolist()]])

  # Train scikit gbm
  # TODO: grid-search
  distribution = "bernoulli"
  ntrees = 150
  min_rows = 1
  max_depth = 2
  learn_rate = .01
  nbins = 20

  gbm_sci = ensemble.GradientBoostingClassifier(learning_rate=learn_rate, n_estimators=ntrees, max_depth=max_depth,
                                                min_samples_leaf=min_rows, max_features=None)
  gbm_sci.fit(X_train,y_train)

  # Generate testing dataset
  test_rows = 2000
  test_cols = 10

  #  Generate variables V1, ... V10
  X_test = np.random.randn(test_rows, test_cols)

  #  y = +1 if sum_i x_{ij}^2 > chisq median on 10 df
  y_test = np.asarray([1 if rs > scipy.stats.chi2.ppf(0.5, 10) else -1 for rs in [sum(r) for r in
                                                                                  np.multiply(X_test,X_test).tolist()]])

  # Score (AUC) the scikit gbm model on the test data
  auc_sci = roc_auc_score(y_test, gbm_sci.predict_proba(X_test)[:,1])

  # Compare this result to H2O
  train_h2o = H2OFrame(zip(*np.column_stack((y_train, X_train)).tolist()))
  test_h2o = H2OFrame(zip(*np.column_stack((y_test, X_test)).tolist()))

  gbm_h2o = h2o.gbm(x=train_h2o[1:], y=train_h2o["C1"].asfactor(), distribution=distribution, ntrees=ntrees,
                    min_rows=min_rows, max_depth=max_depth, learn_rate=learn_rate, nbins=nbins)
  gbm_perf = gbm_h2o.model_performance(test_h2o)
  auc_h2o = gbm_perf.auc()

  #Log.info(paste("scikit AUC:", auc_sci, "\tH2O AUC:", auc_h2o))
  assert abs(auc_h2o - auc_sci) < 1e-2, "h2o (auc) performance degradation, with respect to scikit. h2o auc: {0} " \
                                        "scickit auc: {1}".format(auc_h2o, auc_sci)


if __name__ == "__main__":
  pyunit_utils.standalone_test(bernoulli_synthetic_data_gbm_medium)
else:
  bernoulli_synthetic_data_gbm_medium()
