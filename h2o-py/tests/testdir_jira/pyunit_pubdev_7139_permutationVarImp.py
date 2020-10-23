from builtins import range
import sys, os

sys.path.insert(1, os.path.join("..", ".."))
# import sys
# sys.path.insert(1,"../../")
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from tests import pyunit_utils

from h2o.model.permutation_varimp import permutation_varimp


def gbm_model_build():
    # Log.info("Importing prostate.csv data...\n")
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))

    # Log.info("Converting CAPSULE and RACE columns to factors...\n")
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()

    # Import prostate_train.csv as numpy array for scikit comparison
    # trainData = np.loadtxt(pyunit_utils.locate("smalldata/logreg/prostate_train.csv"), delimiter=',', skiprows=1)

    ntrees = 100
    learning_rate = 0.1
    depth = 5
    min_rows = 10
    # Build H2O GBM classification model:

    gbm_h2o = H2OGradientBoostingEstimator(ntrees=ntrees, learn_rate=learning_rate,
                                           max_depth=depth,
                                           min_rows=min_rows,
                                           distribution="bernoulli")
    gbm_h2o.train(x=list(range(1, prostate_train.ncol)), y="CAPSULE", training_frame=prostate_train)
    # gbm_h2o.model_performance()

    # Log.info("Importing prostate_test.csv data...\n")
    prostate_test = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_test.csv"))

    # Log.info("Converting CAPSULE and RACE columns to factors...\n")
    prostate_test["CAPSULE"] = prostate_test["CAPSULE"].asfactor()

    # Doing PFI on test data vs train data: In the end, you need to decide whether you want to know how much the 
    # model relies on each feature for making predictions (-> training data) or how much the feature contributes to 
    # the performance of the model on unseen data (-> test data). To the best of my knowledge, there is no research 
    # addressing the question of training vs. test data 
    
    return gbm_h2o, prostate_test


def metrics_testing():
    model, fr = gbm_model_build()
    # case H2OFrame
    pm_h2o_df = permutation_varimp(model, fr, use_pandas=False, metric="auc")
    for col in range(0, pm_h2o_df.ncols):
        assert pm_h2o_df[0, col] > 0.0

    # case pandas
    pm_pd_df = permutation_varimp(model, fr, use_pandas=True, metric="auc")
    for col in pm_pd_df.columns:
        assert pm_pd_df.loc[0][col] > 0.0 

    metrics = ["mse", "rmse", "auc", "logloss"]
    for metric in metrics:
        pd_pfi = permutation_varimp(model, fr, use_pandas=False, metric=metric)
        for col in range(0, pd_pfi.ncols):
            assert pd_pfi[0, col] > 0.0


if __name__ == "__main__":
    pyunit_utils.standalone_test(metrics_testing)
else:
    metrics_testing()
