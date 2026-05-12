import sys

from h2o.exceptions import H2OResponseError

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_derived_model():
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset_col = "offset"
    offset = h2o.H2OFrame([[.5]] * cars.nrows)
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

    print("-- derived model with control variables --")
    glm_model_derived_cv = glm_model_cv.make_derived_glm_model(dest="derived_cv")
    print(glm_model_derived_cv)
    metrics_derived_cv = glm_model_derived_cv.training_model_metrics()
    print(metrics_derived_cv)

    print("-- Model with remove offset effects --")
    glm_model_ro = H2OGeneralizedLinearEstimator(family="binomial", remove_offset_effects=True,
                                                 generate_scoring_history=True,
                                                 score_each_iteration=True, seed=0xC0FFEE)
    glm_model_ro.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    print(glm_model_ro)
    metrics_ro = glm_model_ro.training_model_metrics()
    print(metrics_ro)

    print("-- derived model with remove offset effects --")
    glm_model_derived_ro = glm_model_ro.make_derived_glm_model(dest="derived_ro")
    print(glm_model_derived_ro)
    metrics_derived_ro = glm_model_derived_ro.training_model_metrics()
    print(metrics_derived_ro)

    print("-- Model with control variables and remove offset effects --")
    glm_model_cv_ro = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"],
                                                    remove_offset_effects=True, generate_scoring_history=True,
                                                    score_each_iteration=True, seed=0xC0FFEE)
    glm_model_cv_ro.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    print(glm_model_cv_ro)
    metrics_cv_ro = glm_model_cv_ro.training_model_metrics()
    print(metrics_cv_ro)

    print("-- derived model with control variables and remove offset effects disabled --")
    glm_model_derived_cv_ro = glm_model_cv_ro.make_derived_glm_model(dest="all_false")
    print(glm_model_derived_cv_ro)
    metrics_derived_cv_ro = glm_model_derived_cv_ro.training_model_metrics()
    print(metrics_derived_cv_ro)

    print("-- derived model with control variables enabled and remove offset effects disabled --")
    glm_model_derived_cv_true_ro_false = glm_model_cv_ro.make_derived_glm_model(dest="cv_true",
                                                                                remove_control_variables_effects=True)
    print(glm_model_derived_cv_true_ro_false)
    metrics_derived_cv_true_ro_false = glm_model_derived_cv_true_ro_false.training_model_metrics()
    print(metrics_derived_cv_true_ro_false)

    print("-- derived model with control variables disabled and remove offset effects enabled --")
    glm_model_derived_cv_false_ro_true = glm_model_cv_ro.make_derived_glm_model(dest="ro_true",
                                                                                remove_offset_effects=True)
    print(glm_model_derived_cv_false_ro_true)
    metrics_derived_cv_false_ro_true = glm_model_derived_cv_false_ro_true.training_model_metrics()
    print(metrics_derived_cv_false_ro_true)

    # predictions with  basic model
    predictions = glm_model.predict(cars).as_data_frame()
    # predictions with control variables enabled
    predictions_cv = glm_model_cv.predict(cars).as_data_frame()
    # predict with derived model
    predictions_derived_cv = glm_model_derived_cv.predict(cars).as_data_frame()
    # predictions with control variables enabled
    predictions_ro = glm_model_ro.predict(cars).as_data_frame()
    # predict with derived model
    predictions_derived_ro = glm_model_derived_ro.predict(cars).as_data_frame()
    # predictions with control variables and remove offset effects enabled
    predictions_cv_ro = glm_model_cv_ro.predict(cars).as_data_frame()
    # predictions with derived model control variables enabled and remove offset effect enabled
    predictions_derived_cv_ro = glm_model_derived_cv_ro.predict(cars).as_data_frame()
    # predict with derived model control variables enabled and remove offset effect disabled
    predictions_derived_cv_true_ro_false = glm_model_derived_cv_true_ro_false.predict(cars).as_data_frame()
    # predict with derived model control variables disabled and remove offset effect enabled
    predictions_derived_cv_false_ro_true = glm_model_derived_cv_false_ro_true.predict(cars).as_data_frame()

    # check the coefficients
    for k in glm_model.coef().keys():
        pyunit_utils.assert_equals(glm_model.coef()[k], glm_model_derived_cv.coef().get(k, float("NaN")),
                                   f"Coefficient {k} differs!")
        pyunit_utils.assert_equals(glm_model.coef()[k], glm_model_derived_cv_ro.coef().get(k, float("NaN")),
                                   f"Coefficient {k} differs!")

    # check predictions are the same
    for i in range(predictions.shape[0]):
        pyunit_utils.assert_equals(predictions.iloc[i, 1], predictions_derived_cv.iloc[i, 1],
                                   f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions.iloc[i, 1], predictions_derived_ro.iloc[i, 1],
                                   f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions.iloc[i, 1], predictions_derived_cv_ro.iloc[i, 1],
                                   f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions_cv.iloc[i, 1], predictions_derived_cv_true_ro_false.iloc[i, 1],
                                   f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions_ro.iloc[i, 1], predictions_derived_cv_false_ro_true.iloc[i, 1],
                                   f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions_derived_cv_ro.iloc[i, 1], predictions.iloc[i, 1],
                                   f"{i}th prediction differs!")


    # check predictions differ
    for i in range(predictions.shape[0]):
        pyunit_utils.assert_not_equal(predictions.iloc[i, 1], predictions_cv.iloc[i, 1],
                                      f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_not_equal(predictions.iloc[i, 1], predictions_ro.iloc[i, 1],
                                      f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_not_equal(predictions.iloc[i, 1], predictions_cv_ro.iloc[i, 1],
                                      f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_not_equal(predictions_derived_cv_false_ro_true.iloc[i, 1],
                                      predictions_derived_cv_true_ro_false.iloc[i, 1],
                                      f"Predictions at position {i} should differ but they don't!")

    print(glm_model_cv.scoring_history())
    print(glm_model_derived_cv_true_ro_false.scoring_history())

    # check scoring history are the same
    pyunit_utils.assert_equal_scoring_history(glm_model, glm_model_derived_cv,
                                              ["objective", "negative_log_likelihood"])
    pyunit_utils.assert_equal_scoring_history(glm_model_cv, glm_model_derived_cv_true_ro_false,
                                              ["objective", "negative_log_likelihood", "deviance_train", "lambda"])
    pyunit_utils.assert_equal_scoring_history(glm_model_ro, glm_model_derived_cv_false_ro_true,
                                              ["objective", "negative_log_likelihood", "deviance_train", "lambda"])
    pyunit_utils.assert_equal_scoring_history(glm_model_derived_cv, glm_model_derived_cv_ro,
                                              ["objective", "negative_log_likelihood", "deviance_train", "lambda"])

    # should fail
    try:
        glm_model_ro.make_derived_glm_model(dest="ro_true", remove_offset_effects=True)
        assert False, "Should have throw exception."
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "GLM wasn't trained with both control variables and with remove offset effects feature set, the remove_control_variables_effects and remove_offset_effects features cannot be used." in temp, \
            "Wrong exception was received."

    try:
        glm_model_cv_ro.make_derived_glm_model(dest="ro_true", remove_offset_effects=True,
                                               remove_control_variables_effects=True)
        assert False, "Should have throw exception."
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "The remove_control_variables_effects and remove_offset_effects feature cannot be used together. It produces the same model as the main model." in temp, \
            "Wrong exception was received."


    print("-- Model with control variables and remove offset effects with validation dataset --")
    glm_model_cv_ro_valid = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"],
                                                          remove_offset_effects=True, generate_scoring_history=True,
                                                          score_each_iteration=True, seed=0xC0FFEE)
    glm_model_cv_ro_valid.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, validation_frame=cars, offset_column=offset_col)
    print(glm_model_cv_ro_valid)
    metrics_cv_ro_valid_train = glm_model_cv_ro_valid.training_model_metrics()
    print(metrics_cv_ro_valid_train)
    metrics_cv_ro_valid_valid = glm_model_cv_ro_valid.model_performance(test_data=None, train=False, valid=True)

    glm_model_derived_cv_ro_valid = glm_model_cv_ro_valid.make_derived_glm_model(dest="all_false_valid")
    print(glm_model_derived_cv_ro_valid)
    metrics_derived_cv_ro_valid_train = glm_model_derived_cv_ro_valid.training_model_metrics()
    print(metrics_derived_cv_ro_valid_train)
    metrics_derived_cv_ro_valid_valid = glm_model_derived_cv_ro_valid.model_performance(test_data=None, train=False, valid=True)
    print(metrics_derived_cv_ro_valid_valid)
    
    metrics_to_check = ['AUC', 'Gini', 'MSE', 'RMSE', 'AIC']
    for metric in metrics_to_check:
        pyunit_utils.assert_not_equal(metrics_cv_ro_valid_train[metric], metrics_derived_cv_ro_valid_train[metric])
        pyunit_utils.assert_not_equal(metrics_cv_ro_valid_valid[metric], metrics_derived_cv_ro_valid_valid[metric])
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_derived_model)
else:
    glm_derived_model()
