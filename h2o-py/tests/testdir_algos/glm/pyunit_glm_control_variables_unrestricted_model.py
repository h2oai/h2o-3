import sys

from h2o.exceptions import H2OResponseError

sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_control_variables_unrestricted_model():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    glm_model = H2OGeneralizedLinearEstimator(family="binomial", score_each_iteration=True, seed=0xC0FFEE)
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    print("-- Model without control variables --")
    metrics = glm_model.training_model_metrics()
    print(metrics)
    print(glm_model)

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], score_each_iteration=True, seed=0xC0FFEE)
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    print("-- Model with control variables and score each iteration true --")
    print(glm_model_2)
    metrics_2 = glm_model_2.training_model_metrics()
    print(metrics_2)

    glm_model_unrestricted = glm_model_2.make_unrestricted_glm_model()

    print("-- Unrestricted model with control variables --")
    print(glm_model_unrestricted)
    metrics_unrestricted = glm_model_unrestricted.training_model_metrics()
    print(metrics_unrestricted)

    # score each iteration false case
    glm_model_3 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], score_each_iteration=False, seed=0xC0FFEE)
    glm_model_3.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    print("-- Model with control variables score each iteration false --")
    print(glm_model_3)
    metrics_3 = glm_model_3.training_model_metrics()
    print(metrics_3)

    glm_model_4 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], score_each_iteration=True, seed=0xC0FFEE)
    glm_model_4.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, validation_frame=cars)

    print("-- Model with control variables score each iteration true and validation dataset --")
    print(glm_model_4)
    metrics_4 = glm_model_4.training_model_metrics()
    print(metrics_4)

    # predictions with control variables disabled
    predictions_train = glm_model.predict(cars).as_data_frame()
    # predictions with control variables enabled
    predictions_train_2 = glm_model_2.predict(cars).as_data_frame()
    # predict with unrestricted model
    predictions_unrestricted = glm_model_unrestricted.predict(cars).as_data_frame()

    print("predictions with control variables disabled")
    print(predictions_train)
    print("predictions with control variables enabled")
    print(predictions_train_2)
    print("predict with unrestricted model")
    print(predictions_unrestricted)

    print(f"glm_model.default_threshold() = {glm_model.default_threshold()}")
    print(f"glm_model2.default_threshold() = {glm_model_2.default_threshold()}")
    print(f"glm_model3.default_threshold() = {glm_model_3.default_threshold()}")
    print(f"glm_model4.default_threshold() = {glm_model_4.default_threshold()}")
    print(f"glm_model_unrestricted.default_threshold() = {glm_model_unrestricted.default_threshold()}")

    # check the coefficients
    for k in glm_model.coef().keys():
        pyunit_utils.assert_equals(glm_model.coef()[k], glm_model_unrestricted.coef().get(k, float("NaN")), f"Coefficient {k} differs!")

    # check predictions are the same
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_equals(predictions_train.iloc[i, 0], predictions_unrestricted.iloc[i, 0], f"{i}th prediction differs!")
        pyunit_utils.assert_equals(predictions_train.iloc[i, 1], predictions_unrestricted.iloc[i, 1], f"{i}th prediction differs!")

    # check model metrics are not the same
    pyunit_utils.check_model_metrics(glm_model, glm_model_unrestricted, "")
    
    # check scoring history are the same
    pyunit_utils.assert_equal_scoring_history(glm_model, glm_model_unrestricted, ["objective", "negative_log_likelihood"])

    # check unrestricted model key     
    assert glm_model_2._model_json["model_id"]['name']+"_unrestricted_model" in glm_model_unrestricted._model_json["model_id"]['name']
    
    # check model already exists
    try:
        H2OGeneralizedLinearEstimator.make_unrestricted_glm_model(glm_model_2)
    except H2OResponseError as e:
        assert "already exists" in str(e)
        
    # check creation a new model if key differ    
    name = "unrestricted"
    glm_model_unrestricted_2 = H2OGeneralizedLinearEstimator.make_unrestricted_glm_model(glm_model_2, name)
    assert glm_model_unrestricted_2._model_json["model_id"]['name'] == name

    # check variable importance tables are differ
    varimp = glm_model_2.varimp(use_pandas=True)
    varimp_unrestricted = glm_model_unrestricted.varimp(use_pandas=True)
    
    year_varimp = varimp[varimp.apply(lambda row: row.astype(str).str.contains('year').any(), axis=1)]["relative_importance"].values[0]
    assert year_varimp == 0

    year_varimp_unrestricted = varimp_unrestricted[varimp_unrestricted.apply(lambda row: row.astype(str).str.contains('year').any(), axis=1)]["relative_importance"].values[0]
    assert year_varimp_unrestricted > 0
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables_unrestricted_model)
else:
    glm_control_variables_unrestricted_model()
