from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def offset_init_train_glm():
    # Connect to a pre-existing cluster
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5]]*398)
    offset.set_names(["x1"])
    cars = cars.cbind(offset)

    # offset_column passed in the train method
    glm_train = H2OGeneralizedLinearEstimator(family = "binomial")
    glm_train.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars, offset_column="x1")
    predictions_train = glm_train.predict(cars).as_data_frame()

    # offset_column passed in estimator init
    glm_init = H2OGeneralizedLinearEstimator(offset_column="x1", family="binomial")
    glm_init.train(x=list(range(2,8)), y="economy_20mpg", training_frame=cars)
    predictions_init = glm_init.predict(cars).as_data_frame()

    # case the both offset column parameters are set and only the parameter in train will be used
    glm_init_train = H2OGeneralizedLinearEstimator(offset_column="x1-test", family="binomial")
    glm_init_train.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars, offset_column="x1")
    predictions_init_train = glm_init_train.predict(cars).as_data_frame()

    assert predictions_train.equals(predictions_init), "Expected predictions of a model with offset_column in train method has to be same as predictions of a model with offset_column in constructor."
    assert predictions_train.equals(predictions_init_train), "Expected predictions of a model with offset_column in train method has to be same as predictions of a model with offset_column in both constructor and init."


if __name__ == "__main__":
    pyunit_utils.standalone_test(offset_init_train_glm)
else:
    offset_init_train_glm()
