from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator

def algo_pr_auc_test():
    '''
    This pyunit test is written to expose the pr_auc for all binomial runs of all algos per PUBDEV-5665.
    '''

    seed = 123456789
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()

    # Build H2O GBM classification model:
    gbm_h2o = H2OGradientBoostingEstimator(ntrees=10, learn_rate=0.1, max_depth=4, min_rows=10,
                                           distribution="bernoulli", seed=seed)
    gbm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)

    # Build H2O GLM classification model:
    glm_h2o = H2OGeneralizedLinearEstimator(family='binomial', seed=seed)
    glm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)

    rf_h2o = H2ORandomForestEstimator(ntrees=10, score_tree_interval=0)
    rf_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)

    dl_h2o = H2ODeepLearningEstimator(distribution='bernoulli', seed=seed, hidden=[2,2])
    dl_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)

    print("Printing model and notice the pr_auc field....")
    print(dl_h2o)
    print("precision/recall AUC for gbm is {0}, for glm is {1},\n for rf is {2}, for deeplearning is"
          " {3}".format(gbm_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"],
                        glm_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"],
                        rf_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"],
                        dl_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"]))

    assert abs(gbm_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"]-
               glm_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"]) < 0.9, \
        "problem with pr_auc values"

    assert abs(rf_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"]-
               dl_h2o._model_json["output"]["training_metrics"]._metric_json["pr_auc"]) < 0.9, \
        "problem with pr_auc values"

if __name__ == "__main__":
    pyunit_utils.standalone_test(algo_pr_auc_test)
else:
    algo_pr_auc_test()