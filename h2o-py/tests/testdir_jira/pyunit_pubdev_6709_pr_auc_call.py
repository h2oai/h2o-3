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
    This pyunit test is written to make sure we can call pr_auc() on all binomial models.
    '''

    seed = 123456789
    prostate_train = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate_train.csv"))
    prostate_train["CAPSULE"] = prostate_train["CAPSULE"].asfactor()

    # Build H2O GBM classification model:
    gbm_h2o = H2OGradientBoostingEstimator(ntrees=10, learn_rate=0.1, max_depth=4, min_rows=10,
                                           distribution="bernoulli", seed=seed)
    gbm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
    print("***************************   Printing GBM model")
    print(gbm_h2o)
    print("pr_auc for GBM model is {0}".format(gbm_h2o.pr_auc()))

    # Build H2O GLM classification model:
    glm_h2o = H2OGeneralizedLinearEstimator(family='binomial', seed=seed)
    glm_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
    print("***************************   Printing GLM model")
    print(glm_h2o)  # glm scoring history does not contain AUC, and hence no pr_auc
    print("pr_auc for GLM model is {0}".format(glm_h2o.pr_auc()))
    
    rf_h2o = H2ORandomForestEstimator(ntrees=10, score_tree_interval=0)
    rf_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
    print("***************************   Printing random forest model")
    print(rf_h2o)
    print("pr_auc for Random Forest model is {0}".format(rf_h2o.pr_auc()))

    dl_h2o = H2ODeepLearningEstimator(distribution='bernoulli', seed=seed, hidden=[2,2])
    dl_h2o.train(x=list(range(1,prostate_train.ncol)),y="CAPSULE", training_frame=prostate_train)
    print("***************************   Printing deeplearning model")
    print(dl_h2o)
    print("pr_auc for deeplearning model is {0}".format(dl_h2o.pr_auc()))

    assert abs(gbm_h2o.pr_auc()-glm_h2o.pr_auc()) < 0.9, \
        "problem with pr_auc values"

    assert abs(rf_h2o.pr_auc()-dl_h2o.pr_auc()) < 0.9, \
        "problem with pr_auc values"

    assert abs(rf_h2o.pr_auc()-glm_h2o.pr_auc()) < 0.9, \
        "problem with pr_auc values"

    # try to call pr_auc() for regression.  Should encounter error.
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    myY = "GLEASON"
    myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    h2o_model = H2OGeneralizedLinearEstimator(family="gaussian", link="identity",alpha=0.5, Lambda=0)
    h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
    try:
        print(h2o_model.pr_auc())
        assert 1==2, "pr_auc() should raise an error for multinomial but did not."
    except:
        pass

if __name__ == "__main__":
    pyunit_utils.standalone_test(algo_pr_auc_test)
else:
    algo_pr_auc_test()
