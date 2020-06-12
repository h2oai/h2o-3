setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# simple test to make sure we can invoke early stop in R for GLM.  Heavy testing is done in Python.  We
# are just testing the client API.
test.model.glm.early.stop <- function() {
    data <- h2o.importFile(path = locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    data$C1 <- h2o.asfactor(data$C1)
    data$C2 <- h2o.asfactor(data$C2)
    data$C3 <- h2o.asfactor(data$C3)
    data$C4 <- h2o.asfactor(data$C4)
    data$C5 <- h2o.asfactor(data$C5)
    data$C6 <- h2o.asfactor(data$C6)
    data$C7 <- h2o.asfactor(data$C7)
    data$C8 <- h2o.asfactor(data$C8)
    data$C9 <- h2o.asfactor(data$C9)
    data$C10 <- h2o.asfactor(data$C10)
    splits = h2o.splitFrame(data, ratios=c(0.8), seed=12345)
    train = splits[[1]]
    valid = splits[[2]]
    glm_early_stop <- h2o.glm(y = "C21", x = c(1:20), training_frame = train, validation_frame = valid,
    family = "gaussian", stopping_rounds=3, stopping_metric="rmse", stopping_tolerance=0.1, score_each_iteration=TRUE)
    expect_true(length(glm_early_stop@model$scoring_history_early_stop) > 0, "Early stop is not working")
    glm_lambda_search <- h2o.glm(y = "C21", x = c(1:20), training_frame = train, validation_frame = valid,
            family = "gaussian", stopping_rounds=3, stopping_metric="rmse", stopping_tolerance=0.1, lambda_search=TRUE, nlambdas=3)
    expect_true(is.null(glm_lambda_search@model$scoring_history_early_stop), "Early stop should have been disabled for lambda_search=TRUE but is not")
}

doTest("GLM early stop test with Gaussian family", test.model.glm.early.stop)
