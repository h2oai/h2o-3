import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_control_variables_generate_scoring_history():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    glm_model = H2OGeneralizedLinearEstimator(family="binomial", generate_scoring_history=True, 
                                              score_each_iteration=True)
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    sc = glm_model._model_json["output"]["scoring_history"]
    print(sc)
    sc_header = sc.col_header
    assert "negative_log_likelihood" in sc_header

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", generate_scoring_history=True, 
                                                score_each_iteration=True, control_variables=["year"])
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)
    sc_2 = glm_model_2._model_json["output"]["scoring_history"]
    print(sc_2)

    sc_header_2 = sc_2.col_header
    assert "unrestricted_negative_log_likelihood" in sc_header_2

    glm_model_3 = H2OGeneralizedLinearEstimator(family="binomial", generate_scoring_history=True, 
                                                control_variables=["year"], score_each_iteration=True)
    glm_model_3.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars, validation_frame=cars)
    sc_3 = glm_model_3._model_json["output"]["scoring_history"]
    print(sc_3)

    sc_header_3 = sc_3.col_header
    assert "unrestricted_negative_log_likelihood" in sc_header_3
    assert "validation_auc" in sc_header_3


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables_generate_scoring_history)
else:
    glm_control_variables_generate_scoring_history()
