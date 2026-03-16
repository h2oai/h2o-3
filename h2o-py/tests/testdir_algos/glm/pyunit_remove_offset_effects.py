import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_remove_offset_effects():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5]] * cars.nrows)
    offset.set_names(["offset"])
    cars = cars.cbind(offset)

    glm_model = H2OGeneralizedLinearEstimator(family="binomial")
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train = glm_model.predict(cars).as_data_frame()
    print(glm_model._model_json["output"]["scoring_history"])

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", generate_scoring_history=True)
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train_2 = glm_model_2.predict(cars).as_data_frame()
    print(glm_model_2._model_json["output"]["scoring_history"])

    glm_model_roe = H2OGeneralizedLinearEstimator(family="binomial", offset_column="offset", remove_offset_effects=True)
    glm_model_roe.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train_roe = glm_model_roe.predict(cars).as_data_frame()
    print(glm_model_roe._model_json["output"]["scoring_history"])

    glm_model_roe_2 = H2OGeneralizedLinearEstimator(family="binomial",  offset_column="offset", remove_offset_effects=True,
                                                   generate_scoring_history=True)
    glm_model_roe_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    predictions_train_roe2 = glm_model_roe_2.predict(cars).as_data_frame()
    print(glm_model_roe_2._model_json["output"]["scoring_history"])

    # check model metrics are not the same
    try:
        pyunit_utils.check_model_metrics(glm_model, glm_model_roe, "")
    except AssertionError as err:
        assert "Scoring history is not the same" in str(err)
    else:
        assert False, "Expected check_model_metrics to fail because scoring history should differ " \
                      "between glm_model and glm_model_roe"

    # check predictions are different
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_not_equal(predictions_train.iloc[i, 1], predictions_train_roe.iloc[i, 1], 
                                      f"Predictions at position {i} should differ but they don't!")

    # check predictions are the same with and without generate_scoring history
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_equals(predictions_train.iloc[i, 1], predictions_train_2.iloc[i, 1], 
                                   f"Predictions at position {i} should not differ but they do!")
        pyunit_utils.assert_equals(predictions_train_roe.iloc[i, 1], predictions_train_roe2.iloc[i, 1], 
                                   f"Predictions at position {i} should not differ but they do!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_remove_offset_effects)
else:
    glm_remove_offset_effects()
