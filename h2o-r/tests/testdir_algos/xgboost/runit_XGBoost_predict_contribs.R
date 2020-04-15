setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.predict_contribs <- function() {
    expect_true(require("xgboost"))

    housing_hex <- h2o.importFile(locate("smalldata/gbm_test/BostonHousing.csv"))

    # Train an H2O model
    set.seed(42)
    h2o_model <- h2o.xgboost(training_frame = housing_hex, y = "medv")
    h2o_predict <- h2o.predict(h2o_model, housing_hex)
    
    # Train a native XGBoost model
    xgb_params <- as.list(t(h2o_model@model$native_parameters$value))
    names(xgb_params) <- h2o_model@model$native_parameters$name
    housing_df <- as.data.frame(housing_hex)
    housing_dm <- xgb.DMatrix(as.matrix(housing_df[, c(1:(ncol(housing_df)-1))]), label = housing_df$medv)
    xgb_model <- xgboost(params = xgb_params, data = housing_dm, nrounds = as.integer(xgb_params$nround))
    xgb_predict <- predict(xgb_model, housing_dm)

    print(h2o.rmse(h2o_model))

    # Predictions of H2O XGboost and Native XGBoost should be in agreement
    expect_equal(as.data.frame(h2o_predict)$predict, xgb_predict, tolerance = 1e-6)

    # Now calculate Shapley values for both models
    h2o_contribs <- as.matrix(h2o.predict_contributions(h2o_model, housing_hex))
    xgb_contribs <- predict(xgb_model, housing_dm, predcontrib = TRUE)
    
    print(head(h2o_contribs))
    print(head(xgb_contribs))

    # Rename BiasTerm to match XGBoost
    colnames(h2o_contribs) <- c(colnames(h2o_contribs)[1:(ncol(h2o_contribs)-1)], "BIAS")
    
    expect_equal(h2o_contribs, xgb_contribs, 1e-6)
}

doTest("GBM Test: Classification with 50 categorical level predictor", test.XGBoost.predict_contribs)
