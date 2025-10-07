import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_control_variables():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    glm_model = H2OGeneralizedLinearEstimator(family="binomial")
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    
    predictions_train = glm_model.predict(cars).as_data_frame()
    metrics = glm_model.training_model_metrics()
    #print(metrics)
    print(glm_model._model_json["output"]["scoring_history"])

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", generate_scoring_history=True)
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train_2 = glm_model_2.predict(cars).as_data_frame()
    metrics_2 = glm_model_2.training_model_metrics()
    #print(metrics_2)
    print(glm_model_2._model_json["output"]["scoring_history"])

    glm_model_cv = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"])
    glm_model_cv.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    
    predictions_train_cv = glm_model_cv.predict(cars).as_data_frame()
    metrics_cv = glm_model_cv.training_model_metrics()
    #print(metrics_cv)
    print(glm_model_cv._model_json["output"]["scoring_history"])

    glm_model_cv_2 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"], 
                                                   generate_scoring_history=True)
    glm_model_cv_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    predictions_train_cv2 = glm_model_cv_2.predict(cars).as_data_frame()
    metrics_cv_2 = glm_model_cv_2.training_model_metrics()
    #print(metrics_cv_2)
    print(glm_model_cv_2._model_json["output"]["scoring_history"])
    
    # check model metrics are not the same
    try:
        pyunit_utils.check_model_metrics(glm_model, glm_model_cv, "")
    except AssertionError as err:
        assert "Scoring history is not the same" in str(err)
    
    # check predictions are different
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_not_equal(predictions_train.iloc[i, 1], predictions_train_cv.iloc[i, 1], f"Predictions at position {i} should differ but they don't!")
    
    # check predictions are the same with and without generate_scoring history
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_equals(predictions_train.iloc[i, 1], predictions_train_2.iloc[i, 1], f"Predictions at position {i} should not differ but they do!")
        pyunit_utils.assert_equals(predictions_train_cv.iloc[i, 1], predictions_train_cv2.iloc[i, 1], f"Predictions at position {i} should not differ but they do!")




if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables)
else:
    glm_control_variables()
