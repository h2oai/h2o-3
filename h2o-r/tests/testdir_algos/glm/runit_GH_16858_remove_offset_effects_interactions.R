setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# GH-16858: remove_offset_effects has to work together with interactions.
# A normal offset model with interactions (fixed seed) has to predict exactly like the
# remove_offset_effects model when the offset column is manually zeroed out.
glm_remove_offset_effects_interactions <- function() {
    seed <- 1234
    interactions <- c("power", "weight")

    cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
    cars <- cars[!is.na(cars$economy_20mpg), ]
    cars$economy_20mpg <- as.factor(cars$economy_20mpg)
    # a varying offset column (a constant offset is a uniform eta shift and cannot reveal per-row
    # interplay between offset removal and interaction-expanded columns)
    set.seed(seed)
    cars$offset <- as.h2o(data.frame(offset = runif(nrow(cars), -1.0, 1.0)))

    predictors <- c("cylinders", "displacement", "power", "weight", "acceleration", "year")

    # normal offset model with interactions (generate_scoring_history exercises the restricted deviance path)
    glm_model <- h2o.glm(x = predictors, y = "economy_20mpg", training_frame = cars, family = "binomial",
                         seed = seed, interactions = interactions, offset_column = "offset",
                         generate_scoring_history = TRUE)
    predictions <- as.data.frame(h2o.predict(glm_model, cars))
    perf <- h2o.performance(glm_model, cars)

    # same model with remove_offset_effects enabled
    glm_model_roe <- h2o.glm(x = predictors, y = "economy_20mpg", training_frame = cars, family = "binomial",
                             seed = seed, interactions = interactions, offset_column = "offset",
                             generate_scoring_history = TRUE, remove_offset_effects = TRUE)
    predictions_roe <- as.data.frame(h2o.predict(glm_model_roe, cars))
    perf_roe <- h2o.performance(glm_model_roe, cars)

    # manually remove the offset effect by zeroing the offset column
    cars$offset <- 0
    predictions_manual <- as.data.frame(h2o.predict(glm_model, cars))
    perf_manual <- h2o.performance(glm_model, cars)

    mse_with_offset <- h2o.mse(perf)
    mse_manual <- h2o.mse(perf_manual)
    mse_roe <- h2o.mse(perf_roe)
    expect_true(abs(mse_with_offset - mse_manual) > 1e-6,
                "MSE with offset should differ from MSE with offset effects manually removed")
    expect_equal(mse_manual, mse_roe, tolerance = 1e-6)

    # remove_offset_effects predictions must match the manually zeroed-offset predictions row by row
    for (i in seq_len(nrow(predictions))) {
        expect_equal(predictions_manual[i, 2], predictions_roe[i, 2], tolerance = 1e-6,
                     info = sprintf("Predictions at position %d should equal but they don't!", i))
    }

    # keeping the offset must change most predictions (tolerant proportion check - saturated probabilities may
    # coincide to double precision on a few rows, so we don't require every single row to differ)
    num_differ <- sum(abs(predictions[, 2] - predictions_roe[, 2]) > 1e-8)
    expect_true(num_differ > 0.9 * nrow(predictions),
                info = sprintf("Offset model predictions should differ from remove_offset_effects predictions (%d/%d differed)",
                               num_differ, nrow(predictions)))
}

doTest("GLM: remove_offset_effects works with interactions", glm_remove_offset_effects_interactions)
