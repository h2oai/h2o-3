from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Verify scoring history generation for binomial with IRLSM, cross-validation and validation dataset
def testGLMBinomialScoringHistory():
    col_list_compare = ["iterations", "objective", "negative_log_likelihood", "training_logloss", "validation_logloss",
                        "training_classification_error", "validation_classification_error", "training_rmse", 
                        "validation_rmse", "training_auc", "validation_auc", "training_pr_auc", "validation_pr_auc",
                        "training_lift", "validation_lift"]
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    for ind in range(10):
        h2o_data[ind] = h2o_data[ind].asfactor()
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    splits_frames = h2o_data.split_frame(ratios=[.8], seed=1234)
    train = splits_frames[0]
    valid = splits_frames[1]
    Y = "C21"
    X = list(range(0,20))

    print("Building model with score_interval=1.  Should generate same model as "
          "score_each_iteration turned on.")
    h2o_model = glm(family="binomial", score_iteration_interval=1)
    h2o_model.train(x=X, y=Y, training_frame=train, validation_frame=valid)
    print("Building model with score_each_iteration turned on.")
    h2o_model_score_each = glm(family="binomial", score_each_iteration=True)
    h2o_model_score_each.train(x=X, y=Y, training_frame=train, validation_frame=valid)
    # scoring history from h2o_model_score_each and h2o_model should be the same
    pyunit_utils.assert_equal_scoring_history(h2o_model_score_each, h2o_model, col_list_compare)

    print("Building model with score_each_iteration turned on, with  CV.")
    h2o_model_score_each_cv = glm(family="binomial", score_each_iteration=True, nfolds=3, fold_assignment='modulo', 
                                  seed=1234)
    h2o_model_score_each_cv.train(x=X, y=Y, training_frame=train, validation_frame=valid)
    print("Building model with score_interval=1, and CV.  Should generate same model as score_each_iteration turned "
          "on, with lambda search and CV.")
    h2o_model_cv = glm(family="binomial", score_iteration_interval=1, nfolds=3, fold_assignment='modulo', seed=1234)
    h2o_model_cv.train(x=X, y=Y, training_frame=train, validation_frame=valid)
    # scoring history from h2o_model_score_each_cv and h2o_model_cv should be the same
    pyunit_utils.assert_equal_scoring_history(h2o_model_score_each_cv, h2o_model_cv, col_list_compare)

    # check if scoring_interval is set to 4, the output should be the same for every fourth iteration
    h2o_model_cv_4th = glm(family="binomial", score_iteration_interval=4, nfolds=3, fold_assignment='modulo', seed=1234)
    h2o_model_cv_4th.train(x=X, y=Y, training_frame=train, validation_frame=valid)
    pyunit_utils.assertEqualScoringHistoryIteration(h2o_model_cv, h2o_model_cv_4th, col_list_compare)


if __name__ == "__main__":
    pyunit_utils.standalone_test(testGLMBinomialScoringHistory)
else:
    testGLMBinomialScoringHistory()
