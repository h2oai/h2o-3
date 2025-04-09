from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_control_variables():

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["name"] = cars["name"].asfactor()
    cars["year"] = cars["year"].asfactor()
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    glm_model = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["name", "power", "year"])
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)


    glm_model = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["name", "power", "year"])
    glm_model.train(x=list(range(0, 8)), y="economy_20mpg", training_frame=cars)

    # predict 
    predictions_train = glm_model.predict(cars).as_data_frame()
    print(predictions_train)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables)
else:
    glm_control_variables()
