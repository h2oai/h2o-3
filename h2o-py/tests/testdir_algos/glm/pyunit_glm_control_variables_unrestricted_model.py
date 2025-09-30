import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_control_variables_unrestricted_model():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    glm_model = H2OGeneralizedLinearEstimator(family="binomial", score_each_iteration=True)
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    metrics = glm_model.training_model_metrics()
    print(metrics)
    print("++++++++++++++++ Model without control variables")
    print(glm_model._model_json["output"]["scoring_history"])

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], score_each_iteration=True)
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    metrics_2 = glm_model_2.training_model_metrics()
    print(metrics_2)
    print("++++++++++++++++ Model with control variables and score each iteration true")
    print(glm_model_2._model_json["output"]["scoring_history"])

    glm_model_unrestricted = H2OGeneralizedLinearEstimator.get_unrestricted_glm_model(glm_model_2, "unrestricted")
    metrics_unrestricted = glm_model_unrestricted.training_model_metrics()
    print(metrics_unrestricted)
    print("++++++++++++++++ Unrestricted model with control variables")
    print(glm_model_unrestricted._model_json["output"]["scoring_history"])

    # score each iteration false case
    glm_model_3 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], score_each_iteration=False)
    glm_model_3.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    metrics_3 = glm_model_3.training_model_metrics()
    print(metrics_3)
    print("++++++++++++++++ Model with control variables score each iteration false")
    print(glm_model_3._model_json["output"]["scoring_history"])

    glm_model_4 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], score_each_iteration=True)
    glm_model_4.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, validation_frame=cars)

    metrics_4 = glm_model_4.training_model_metrics()
    print(metrics_4)
    print("++++++++++++++++ Model with control variables score each iteration true and validation dataset")
    print(glm_model_4._model_json["output"]["scoring_history"])

    predictions_train = glm_model.predict(cars).as_data_frame()
    print(predictions_train)

    predictions_train_2 = glm_model_2.predict(cars).as_data_frame()
    print(predictions_train_2)

    # predict with unrestricted model
    predictions_unrestricted = glm_model_unrestricted.predict(cars).as_data_frame()
    print(predictions_unrestricted)

    # check model metrics are not the same
    pyunit_utils.check_model_metrics(glm_model, glm_model_unrestricted, "")
    
    # check scoring history are the same
    pyunit_utils.assert_equal_scoring_history(glm_model, glm_model_unrestricted, ["objective", "negative_log_likelihood"])

    # check predictions are the same
    pyunit_utils.assert_equals(predictions_train.iloc[0, 1], predictions_unrestricted.iloc[0, 1])
    pyunit_utils.assert_equals(predictions_train.iloc[10, 1], predictions_unrestricted.iloc[10, 1])
    pyunit_utils.assert_equals(predictions_train.iloc[100, 1], predictions_unrestricted.iloc[100, 1])
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables_unrestricted_model)
else:
    glm_control_variables_unrestricted_model()
