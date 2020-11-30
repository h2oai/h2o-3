from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm


# In this test, we check that we can do early-stopping with GLM.  In particular, we check the following conditions
# 1. run the model without early stopping and check that model with early stopping runs for fewer iterations
# 2. for models with early stopping, check that early stopping is correctly done.
# 3. when lambda_search is enabled, early stopping should be disabled
def test_glm_earlyStop():
    early_stop_metrics = ["logloss", "RMSE"]
    early_stop_valid_metrics = ["validation_logloss", "validation_rmse"]
    max_stopping_rounds = 3  # maximum stopping rounds allowed to be used for early stopping metric
    max_tolerance = 0.01  # maximum tolerance to be used for early stopping metric
    bigger_is_better = [False, False]

    print("Preparing dataset....")
    h2o_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    splits_frames = h2o_data.split_frame(ratios=[.8], seed=1234)
    train = splits_frames[0]
    valid = splits_frames[1]

    print("Building model without early stopping.")
    h2o_model_no_early_stop = glm(family="multinomial", score_each_iteration=True)
    h2o_model_no_early_stop.train(x=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9], y="C11", training_frame=train,
                                  validation_frame=valid)
    numIter = len(h2o_model_no_early_stop._model_json["output"]["scoring_history"].cell_values)
    for ind in range(len(early_stop_metrics)):
        print("Building early-stop model")
        h2o_model = glm(family="multinomial", stopping_rounds=max_stopping_rounds, score_each_iteration=True,
                        stopping_metric=early_stop_metrics[ind], stopping_tolerance=max_tolerance)
        h2o_model.train(x=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9], y="C11", training_frame=train, validation_frame=valid)
        metric_list1 = \
            pyunit_utils.extract_field_from_twoDimTable(
                h2o_model._model_json["output"]["scoring_history"].col_header,
                h2o_model._model_json["output"]["scoring_history"].cell_values,
                early_stop_valid_metrics[ind])
        print("Checking if early stopping has been done correctly for {0}.".format(early_stop_metrics[ind]))
        assert pyunit_utils.evaluate_early_stopping(metric_list1, max_stopping_rounds, max_tolerance,
                                                    bigger_is_better[ind]), \
            "Early-stopping was not incorrect."
        assert len(h2o_model._model_json["output"]["scoring_history"].cell_values) <= numIter, \
            "Number of iterations without early stop: {0} should be more than with early stop: " \
            "{1}".format(numIter, len(h2o_model._model_json["output"]["scoring_history"].cell_values))

    print("Check if lambda_search=True, early-stop enabled, an error should be thrown.")
    try:
        h2o_model = glm(family="multinomial", score_each_iteration=True, stopping_rounds=max_stopping_rounds, 
                        stopping_metric=early_stop_metrics[ind], stopping_tolerance=max_tolerance, nlambdas=5, 
                        lambda_search=True)
        h2o_model.train(x=[0, 1, 2, 3, 4, 5, 6, 7, 8, 9], y="C11", training_frame=train, validation_frame=valid)
        assert False, "Exception should have been risen when lambda_search=True and early stop is enabled"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("early stop:  cannot run when lambda_search=True.  Lambda_search has its own early-stopping "
                "mechanism" in temp), "Wrong exception was received."
        print("early-stop test passed!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_earlyStop)
else:
    test_glm_earlyStop()
