from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Test GLM multinomial works with alpha array
def test_multinomial_alpha():
    col_list_compare = ["iterations", "objective", "negative_log_likelihood", "training_logloss", "validation_logloss",
                        "training_classification_error", "validation_classification_error"]
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
    training_data = splits_frames[0]
    test_data = splits_frames[1]
    X = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
    Y = "C11"

    print("Building model with score_each_iteration turned on.")
    # test with lambda search on, generate_scoring_history on and off
    model1 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True)
    model1.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    model2 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True)
    model2.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    coef1 = model1.coef()
    coef2 = model2.coef()
    for key in coef1.keys():
        pyunit_utils.assertEqualCoeffDicts(coef1[key], coef2[key], tol=1e-6)

    # test with lambda search off, generate_scoring_history on and off
    model1 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, Lambda=[0, 0.1, 0.01, 0.001])
    model1.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    model2 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, Lambda=[0, 0.1, 0.01, 0.001])
    model2.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    coef1 = model1.coef()
    coef2 = model2.coef()
    for key in coef1.keys():
        pyunit_utils.assertEqualCoeffDicts(coef1[key], coef2[key], tol=1e-6)

    # test with lambda search on, generate_scoring_history on and off, cv on
    model1 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True, nfolds=2, seed=12345)
    model1.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    model2 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=True,
                                           generate_scoring_history=True, nfolds=2, seed=12345)
    model2.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    coef1 = model1.coef()
    coef2 = model2.coef()
    for key in coef1.keys():
        pyunit_utils.assertEqualCoeffDicts(coef1[key], coef2[key], tol=1e-6)

    # test with lambda search off, generate_scoring_history on and off, cv on
    model1 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, nfolds=2, seed=12345,
                                           Lambda=[0, 0.1, 0.01, 0.001])
    model1.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    model2 = glm(family="multinomial", alpha=[0,0.2,0.5,0.8,1], lambda_search=False,
                                           generate_scoring_history=True, nfolds=2, seed=12345,
                                           Lambda=[0, 0.1, 0.01, 0.001])
    model2.train(x=X, y=Y, training_frame=training_data, validation_frame=test_data)
    coef1 = model1.coef()
    coef2 = model2.coef()
    for key in coef1.keys():
        pyunit_utils.assertEqualCoeffDicts(coef1[key], coef2[key], tol=1e-6)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_multinomial_alpha)
else:
    test_multinomial_alpha()
