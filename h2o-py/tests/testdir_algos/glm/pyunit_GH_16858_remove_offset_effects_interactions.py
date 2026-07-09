from builtins import range
import random
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


# GH-16858: remove_offset_effects has to work together with interactions.
# A normal offset model with interactions (fixed seed) has to predict exactly like the
# remove_offset_effects model when the offset column is manually zeroed out.
def glm_remove_offset_effects_interactions():
    seed = 1234
    interactions = ["power", "weight"]

    cars = h2o.upload_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars = cars[cars["economy_20mpg"].isna() == 0]
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()

    # a varying offset column (a constant offset is a uniform eta shift and cannot reveal
    # per-row interplay between offset removal and interaction-expanded columns)
    random.seed(seed)
    offset_col = "offset"
    offset = h2o.H2OFrame([[round(random.uniform(-1.0, 1.0), 4)] for _ in range(cars.nrows)])
    offset.set_names([offset_col])
    cars = cars.cbind(offset)

    # normal offset model with interactions (generate_scoring_history exercises the restricted deviance path)
    glm_model = H2OGeneralizedLinearEstimator(family="binomial", seed=seed, interactions=interactions,
                                              generate_scoring_history=True)
    glm_model.train(x=list(range(2, 8)), y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    predictions_train = glm_model.predict(cars).as_data_frame()
    perf = glm_model.model_performance(cars)

    # same model with remove_offset_effects enabled
    glm_model_roe = H2OGeneralizedLinearEstimator(family="binomial", seed=seed, interactions=interactions,
                                                  generate_scoring_history=True, remove_offset_effects=True)
    glm_model_roe.train(x=list(range(2, 8)), y="economy_20mpg", training_frame=cars, offset_column=offset_col)
    predictions_train_roe = glm_model_roe.predict(cars).as_data_frame()
    perf_roe = glm_model_roe.model_performance(cars)

    # manually remove the offset effect by zeroing the offset column
    cars[offset_col] = 0
    predictions_train_manual = glm_model.predict(cars).as_data_frame()
    perf_manual = glm_model.model_performance(cars)

    mse_with_offset = perf.mse()
    mse_manual = perf_manual.mse()
    mse_roe = perf_roe.mse()
    assert abs(mse_with_offset - mse_manual) > 1e-6, \
        "MSE with offset should differ from MSE with offset effects manually removed"
    pyunit_utils.assert_equals(mse_manual, mse_roe, delta=1e-6)

    # remove_offset_effects predictions must match the manually zeroed-offset predictions row by row
    for i in range(predictions_train.shape[0]):
        pyunit_utils.assert_equals(predictions_train_manual.iloc[i, 1], predictions_train_roe.iloc[i, 1],
                                   delta=1e-6, message=f"Predictions at position {i} should equal but they don't!")

    # keeping the offset must change most predictions (tolerant proportion check - saturated probabilities may
    # coincide to double precision on a few rows, so we don't require every single row to differ)
    n = predictions_train.shape[0]
    num_differ = sum(1 for i in range(n) if abs(predictions_train.iloc[i, 1] - predictions_train_roe.iloc[i, 1]) > 1e-8)
    assert num_differ > 0.9 * n, \
        f"Offset model predictions should differ from remove_offset_effects predictions ({num_differ}/{n} differed)"


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_remove_offset_effects_interactions)
else:
    glm_remove_offset_effects_interactions()
