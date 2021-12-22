import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# test scoring_history for Gaussian family with validation dataset and cv
def testGLMGaussianScoringHistory():
    col_list_compare = ["iterations", "objective", "negative_log_likelihood", "training_rmse", "validation_rmse",
                        "training_mae", "validation_mae", "training_deviance", "validation_deviance", "deviance_train",
                        "deviance_test"]

    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]
    
    # build gaussian model with score_each_interval to true
    model = glm(family="gaussian", score_each_iteration=True, generate_scoring_history=True)
    model.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    # build gaussian model with score_iteration_interval to 1
    model_score_each = glm(family="gaussian", score_iteration_interval=1, generate_scoring_history=True)
    model_score_each.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assert_equal_scoring_history(model, model_score_each, col_list_compare)

    # build gaussian model with score_each_interval to true, with CV
    model_cv = glm(family="gaussian", score_each_iteration=True, nfolds=3, fold_assignment='modulo', seed=1234,
                   generate_scoring_history=True)
    model_cv.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    # build gaussian model with score_iteration_interval to 1, with CV
    model_score_each_cv = glm(family="gaussian", score_iteration_interval=1, nfolds=3, fold_assignment='modulo', 
                              seed=1234, generate_scoring_history=True)
    model_score_each_cv.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assert_equal_scoring_history(model_cv, model_score_each_cv, col_list_compare)
    model_cv_4th = glm(family="gaussian", score_iteration_interval=4, nfolds=3, fold_assignment='modulo', seed=1234,
                       generate_scoring_history=True)
    model_cv_4th.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    pyunit_utils.assertEqualScoringHistoryIteration(model_cv_4th, model_cv, col_list_compare)

if __name__ == "__main__":
    pyunit_utils.standalone_test(testGLMGaussianScoringHistory)
else:
    testGLMGaussianScoringHistory()
