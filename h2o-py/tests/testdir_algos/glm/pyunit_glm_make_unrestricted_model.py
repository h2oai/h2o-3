import sys

from h2o.exceptions import H2OResponseError

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_unrestricted_model():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset_col = "offset"
    offset = h2o.H2OFrame([[.5]]*398)
    offset.set_names([offset_col])
    cars = cars.cbind(offset)

    print("-- Model without control variables and remove offset effects --")
    glm_model = H2OGeneralizedLinearEstimator(family="binomial", score_each_iteration=True, 
                                              generate_scoring_history=True, seed=0xC0FFEE)
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    metrics = glm_model.training_model_metrics()
    print(metrics)
    print(glm_model)

    print("-- Model with control variables --")
    glm_model_cv = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], 
                                                 score_each_iteration=True, generate_scoring_history=True,
                                                 seed=0xC0FFEE)
    glm_model_cv.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    print(glm_model_cv)
    metrics_cv = glm_model_cv.training_model_metrics()
    print(metrics_cv)

    print("-- Unrestricted model with control variables --")
    glm_model_unrestricted_cv = glm_model_cv.make_unrestricted_glm_model(dest="unrestricted_cv")
    print(glm_model_unrestricted_cv)
    metrics_unrestricted_cv = glm_model_unrestricted_cv.training_model_metrics()
    print(metrics_unrestricted_cv)

    print("-- Model with remove offset effects --")
    glm_model_ro = H2OGeneralizedLinearEstimator(family="binomial", remove_offset_effects=True, 
                                                 generate_scoring_history=True,
                                                 score_each_iteration=True, seed=0xC0FFEE)
    glm_model_ro.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    print(glm_model_ro)
    metrics_ro = glm_model_ro.training_model_metrics()
    print(metrics_ro)

    print("-- Unrestricted model with remove offset effects --")
    glm_model_unrestricted_ro = glm_model_cv.make_unrestricted_glm_model(dest="unrestricted_ro")
    print(glm_model_unrestricted_ro)
    metrics_unrestricted_ro = glm_model_unrestricted_ro.training_model_metrics()
    print(metrics_unrestricted_ro)

    print("-- Model with control variables and remove offset effects --")
    glm_model_cv_ro = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], 
                                                    remove_offset_effects=True, generate_scoring_history=True,
                                                    score_each_iteration=True, seed=0xC0FFEE)
    glm_model_cv_ro.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    print(glm_model_cv_ro)
    metrics_cv_ro = glm_model_cv_ro.training_model_metrics()
    print(metrics_cv_ro)

    print("-- Unrestricted model with control variables and remove offset effects disabled --")
    glm_model_unrestricted_cv_ro = glm_model_cv_ro.make_unrestricted_glm_model(dest="all_false")
    print(glm_model_unrestricted_cv_ro)
    metrics_unrestricted_cv_ro = glm_model_unrestricted_cv.training_model_metrics()
    print(metrics_unrestricted_cv_ro)

    print("-- Unrestricted model with control variables enabled and remove offset effects disabled --")
    glm_model_unrestricted_cv_true_ro_false = glm_model_cv_ro.make_unrestricted_glm_model(dest="cv_true", 
                                                                                          control_variables_enabled=True)
    print(glm_model_unrestricted_cv_ro)
    metrics_unrestricted_cv_true_ro_false = glm_model_unrestricted_cv_true_ro_false.training_model_metrics()
    print(metrics_unrestricted_cv_true_ro_false)

    print("-- Unrestricted model with control variables disabled and remove offset effects enabled --")
    glm_model_unrestricted_cv_false_ro_true = glm_model_cv_ro.make_unrestricted_glm_model(dest="ro_true",
                                                                                          remove_offset_effects_enabled=True)
    print(glm_model_unrestricted_cv_false_ro_true)
    metrics_unrestricted_cv_true_ro_false = glm_model_unrestricted_cv_false_ro_true.training_model_metrics()
    print(metrics_unrestricted_cv_true_ro_false)

    # predictions with  basic model
    predictions = glm_model.predict(cars).as_data_frame()
    # predictions with control variables enabled
    predictions_cv = glm_model_cv.predict(cars).as_data_frame()
    # predict with unrestricted model
    predictions_unrestricted_cv = glm_model_unrestricted_cv.predict(cars).as_data_frame()
    # predictions with control variables enabled
    predictions_ro = glm_model_ro.predict(cars).as_data_frame()
    # predict with unrestricted model
    predictions_unrestricted_ro = glm_model_unrestricted_ro.predict(cars).as_data_frame()
    # predictions with control variables and remove offset effects enabled
    predictions_cv_ro = glm_model_cv_ro.predict(cars).as_data_frame()
    # predictions with unrestricted model control variables enabled and remove offset effect enabled
    predictions_unrestricted_cv_ro = glm_model_unrestricted_cv_ro.predict(cars).as_data_frame()
    # predict with unrestricted model control variables enabled and remove offset effect disabled
    predictions_unrestricted_cv_true_ro_false = glm_model_unrestricted_cv_true_ro_false.predict(cars).as_data_frame()
    # predict with unrestricted model control variables disabled and remove offset effect enabled
    predictions_unrestricted_cv_false_ro_true = glm_model_unrestricted_cv_false_ro_true.predict(cars).as_data_frame()

    # check the coefficients
    for k in glm_model.coef().keys():
        pyunit_utils.assert_equals(glm_model.coef()[k], glm_model_unrestricted_cv.coef().get(k, float("NaN")), f"Coefficient {k} differs!")

    # check predictions are the same
    for i in range(predictions.shape[0]):
        pyunit_utils.assert_equals(predictions.iloc[i, 1], predictions_unrestricted_cv.iloc[i, 1], f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions.iloc[i, 1], predictions_unrestricted_ro.iloc[i, 1], f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions.iloc[i, 1], predictions_unrestricted_cv_ro.iloc[i, 1], f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions_cv.iloc[i, 1], predictions_unrestricted_cv_true_ro_false.iloc[i, 1], f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions_ro.iloc[i, 1], predictions_unrestricted_cv_false_ro_true.iloc[i, 1], f"{i}th prediction differs!")
        
    # check predictions differ
    for i in range(predictions.shape[0]):
        pyunit_utils.assert_not_equal(predictions.iloc[i, 1], predictions_cv.iloc[i, 1], f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_not_equal(predictions.iloc[i, 1], predictions_ro.iloc[i, 1], f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_not_equal(predictions.iloc[i, 1], predictions_cv_ro.iloc[i, 1], f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_not_equal(predictions_unrestricted_cv_false_ro_true.iloc[i, 1], predictions_unrestricted_cv_true_ro_false.iloc[i, 1], f"Predictions at position {i} should differ but they don't!")
        
    print(glm_model_cv.scoring_history())
    print(glm_model_unrestricted_cv_true_ro_false.scoring_history())
    
    # check scoring history are the same
    pyunit_utils.assert_equal_scoring_history(glm_model, glm_model_unrestricted_cv, 
                                              ["objective", "negative_log_likelihood"])
    pyunit_utils.assert_equal_scoring_history(glm_model_cv, glm_model_unrestricted_cv_true_ro_false, 
                                              ["objective", "negative_log_likelihood", "deviance_train", "lambda"])
    pyunit_utils.assert_equal_scoring_history(glm_model_ro, glm_model_unrestricted_cv_false_ro_true,
                                              ["objective", "negative_log_likelihood", "deviance_train", "lambda"])
    pyunit_utils.assert_equal_scoring_history(glm_model_unrestricted_cv, glm_model_unrestricted_cv_ro,
                                              ["objective", "negative_log_likelihood", "deviance_train", "lambda"])


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_unrestricted_model)
else:
    glm_unrestricted_model()
