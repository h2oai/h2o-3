from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_remove_offset_effects():
    
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

    # offset_column passed in the train method
    glm_model_remove_offset_effects = H2OGeneralizedLinearEstimator(family="binomial", remove_offset_effects=True)
    glm_model_remove_offset_effects.train(x=list(range(2,8)),y="economy_20mpg", training_frame=cars, 
                                          offset_column=offset_col)

    predictions_train_remove_offset_effects = glm_model_remove_offset_effects.predict(cars).as_data_frame()
    print(predictions_train_remove_offset_effects)

    # metrics with remove offset effects enabled
    perf_remove_offset_effects = glm_model_remove_offset_effects.model_performance(cars)
    print(perf_remove_offset_effects)
    
    # setup offset column to zero to remove its effect
    cars[offset_col] = 0

    # predict with offset effects removed
    predictions_train_remove_offset_manual = glm_model.predict(cars).as_data_frame()
    print(predictions_train_remove_offset_manual)

    # metrics with offset effects removed
    perf_remove_offset_manual = glm_model.model_performance(cars)
    print(perf_remove_offset_manual)

    mse_with_offset = perf.mse()
    mse_remove_offset_manual = perf_remove_offset_manual.mse()
    mse_remove_offset_effects = perf_remove_offset_effects.mse()
    # use tolerance-based comparisons to avoid brittleness with floating point values
    assert abs(mse_with_offset - mse_remove_offset_manual) > 1e-6, \
        "MSE with offset should differ from MSE with offset effects manually removed"
    pyunit_utils.assert_equals(mse_remove_offset_manual, mse_remove_offset_effects, delta=1e-6)

    # check predictions are different
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_not_equal(predictions_train.iloc[i, 1], predictions_train_remove_offset_effects.iloc[i, 1], 
                                      f"Predictions at position {i} should differ but they don't!")
        pyunit_utils.assert_equals(predictions_train_remove_offset_manual.iloc[i, 1], 
                                   predictions_train_remove_offset_effects.iloc[i, 1], 
                                   f"Predictions at position {i} should equal but they don't!")
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_remove_offset_effects)
else:
    glm_remove_offset_effects()
