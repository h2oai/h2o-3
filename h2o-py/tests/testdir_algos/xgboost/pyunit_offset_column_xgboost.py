from builtins import range
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.xgboost import *


def xgboost_offset_column():
    # Connect to a pre-existing cluster
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    offset = h2o.H2OFrame([[.5]]*398)
    offset.set_names(["x1"])
    cars = cars.cbind(offset)

    normal = H2OXGBoostEstimator(ntrees=1, max_depth=1, min_rows=1, learn_rate=1)
    normal.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars)
    predictions_normal = normal.predict(cars).as_data_frame()

    offset = H2OXGBoostEstimator(ntrees=1, max_depth=1, min_rows=1, learn_rate=1, offset_column="x1")
    offset.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars)
    predictions_offset = offset.predict(cars).as_data_frame()

    assert not predictions_normal.equals(predictions_offset)


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_offset_column)
else:
    xgboost_offset_column()
