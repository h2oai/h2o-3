from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def remove_offset_glm():
    
    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    offset_col = "offset"
    offset = h2o.H2OFrame([[.5]]*398)
    offset.set_names([offset_col])
    cars = cars.cbind(offset)

    # offset_column passed in the train method
    glm_model = H2OGeneralizedLinearEstimator(family="binomial")
    glm_model.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    
    # predict with offset
    predictions_train = glm_model.predict(cars).as_data_frame()
    print(predictions_train)
    
    # metrics with offset
    perf = glm_model.model_performance(cars)
    print(perf)
    
    # setup offset column to zero to remove its effect
    cars[offset_col] = 0

    # predict with offset effect removed
    predictions_train_remove_offset = glm_model.predict(cars).as_data_frame()
    print(predictions_train_remove_offset)

    # metrics with offset effect removed
    perf = glm_model.model_performance(cars)
    print(perf)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(remove_offset_glm)
else:
    remove_offset_glm()
