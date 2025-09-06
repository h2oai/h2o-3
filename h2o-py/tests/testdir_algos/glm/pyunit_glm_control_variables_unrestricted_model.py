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

    glm_model = H2OGeneralizedLinearEstimator(family="binomial")
    glm_model.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train = glm_model.predict(cars).as_data_frame()
    metrics = glm_model.training_model_metrics()
    print(metrics)

    glm_model_2 = H2OGeneralizedLinearEstimator(family="binomial", control_variables=["year"])
    glm_model_2.train(x=["name", "power", "year"], y="economy_20mpg", training_frame=cars)

    predictions_train_2 = glm_model_2.predict(cars).as_data_frame()
    metrics_2 = glm_model_2.training_model_metrics()
    
    print(metrics_2)

    glm_model_unrestricted = H2OGeneralizedLinearEstimator.make_unrestricted_glm_model(glm_model_2)

    predictions_train_unrestricted = glm_model_unrestricted.predict(cars).as_data_frame()
    metrics_unrestricted = glm_model_unrestricted.training_model_metrics()
    
    print(metrics_unrestricted)


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_control_variables_unrestricted_model)
else:
    glm_control_variables_unrestricted_model()
