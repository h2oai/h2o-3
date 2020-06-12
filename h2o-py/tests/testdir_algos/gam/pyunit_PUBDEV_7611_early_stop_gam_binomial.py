from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check that we can do early-stopping with GAM.  In particular, we check the following conditions
# 1. run the model without early stopping and check that model with early stopping runs for fewer iterations
# 2. for models with early stopping, check that early stopping is correctly done.
# 3. when lambda_search is enabled, early stopping should be disabled
def test_gam_model_predict():
    print("Checking early-stopping for binomial")
    print("Preparing for data....")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C6"] = h2o_data["C6"].asfactor()
    h2o_data["C7"] = h2o_data["C7"].asfactor()
    h2o_data["C8"] = h2o_data["C8"].asfactor()
    h2o_data["C9"] = h2o_data["C9"].asfactor()
    h2o_data["C10"] = h2o_data["C10"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    splits = h2o_data.split_frame(ratios=[0.8], seed=12345)
    train = splits[0]
    test = splits[1]
    early_stop_metrics = ["logloss", "AUC"]
    early_stop_valid_metrics = ["validation_logloss", "validation_auc"]
    max_stopping_rounds = 3  # maximum stopping rounds allowed to be used for early stopping metric
    max_tolerance = 0.1  # maximum tolerance to be used for early stopping metric
    bigger_is_better = [False, True]

    print("Building a GAM model without early stop")
    h2o_model_no_early_stop = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["C11"],  scale = [0.0001], 
                                                score_each_iteration=True)
    h2o_model_no_early_stop.train(x=list(range(0,20)), y=myY, training_frame=train, validation_frame=test)

    for ind in range(len(early_stop_metrics)):
        print("Building early-stop model")
        h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["C11"], scale = [0.0001], 
                                                    stopping_rounds=max_stopping_rounds,score_each_iteration=True, 
                                                    stopping_metric=early_stop_metrics[ind],
                                                    stopping_tolerance=max_tolerance)
        h2o_model.train(x=list(range(0,20)), y="C21", training_frame=train, validation_frame=test)
        metric_list1 = \
            pyunit_utils.extract_field_from_twoDimTable(
                h2o_model._model_json["output"]["glm_scoring_history"].col_header,
                h2o_model._model_json["output"]["glm_scoring_history"].cell_values,
                early_stop_valid_metrics[ind])
        print("Checking if early stopping has been done correctly for {0}.".format(early_stop_metrics[ind]))
        assert pyunit_utils.evaluate_early_stopping(metric_list1, max_stopping_rounds, max_tolerance,
                                                    bigger_is_better[ind]), \
            "Early-stopping was not incorrect."

    print("Check if lambda_search=True, early-stop enabled, an error should be thrown.")
    try:
        h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["C11"], scale = [0.0001],
                                                stopping_rounds=max_stopping_rounds,score_each_iteration=True,
                                                stopping_metric=early_stop_metrics[ind],
                                                stopping_tolerance=max_tolerance, lambda_search=True, nlambdas=3)
        h2o_model.train(x=list(range(0,20)), y=myY, training_frame=train, validation_frame=test)
        assert False, "Exception should have been risen when lambda_search=True and early stop is enabled"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("early stop:  cannot run when lambda_search=True.  Lambda_search has its own early-stopping "
                "mechanism" in temp), "Wrong exception was received."
        print("early-stop test passed!") 
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
