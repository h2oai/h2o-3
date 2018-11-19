from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def offset_init_train_gbm():
    # Connect to a pre-existing cluster
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5]]*398)
    offset.set_names(["x1"])
    cars = cars.cbind(offset)

    # offset_column passed in the train method
    gbm_train = H2OGradientBoostingEstimator(ntrees=1, max_depth=1, min_rows=1, learn_rate=1)
    gbm_train.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars, offset_column="x1")
    predictions_train = gbm_train.predict(cars).as_data_frame()

    # test offset_column passed in estimator init
    gbm_init = H2OGradientBoostingEstimator(ntrees=1, max_depth=1, min_rows=1, learn_rate=1, offset_column="x1")
    gbm_init.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars)
    predictions_init = gbm_init.predict(cars).as_data_frame()

    # test case the both offset column parameters are set the parameter in train will be used
    gbm_init_train = H2OGradientBoostingEstimator(ntrees=1, max_depth=1, min_rows=1,learn_rate=1, offset_column="x1-test")
    gbm_init_train.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars, offset_column="x1")
    predictions_init_train = gbm_init_train.predict(cars).as_data_frame()

    assert predictions_train.equals(predictions_init), "Expected predictions of a model with offset_column in train method has to be same as predictions of a model with offset_column in constructor."
    assert predictions_train.equals(predictions_init_train), "Expected predictions of a model with offset_column in train method has to be same as predictions of a model with offset_column in both constructor and init."


if __name__ == "__main__":
    pyunit_utils.standalone_test(offset_init_train_gbm)
else:
    offset_init_train_gbm()
