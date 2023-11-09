setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.adaBoost.smoke <- function() {
    f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv"
    data <- h2o.importFile(f)

    # Set predictors and response; set response as a factor
    data["CAPSULE"] <- as.factor(data["CAPSULE"])
    predictors <- c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
    response <- "CAPSULE"

    h2o_adaboost <- h2o.adaBoost(nlearners = 5, x = predictors, y = response, training_frame = data, seed = 1234, weak_learner = "DRF", weak_learner_params = list(ntrees=3, max_depth=2, histogram_type="UniformAdaptive"))
    expect_equal(is.null(h2o_adaboost), FALSE)
    h2o_adaboost <- h2o.adaBoost(nlearners = 5, x = predictors, y = response, training_frame = data, seed = 1234, weak_learner = "GBM", weak_learner_params = list(ntrees=3, max_depth=2, histogram_type="UniformAdaptive"))
    expect_equal(is.null(h2o_adaboost), FALSE)
    h2o_adaboost <- h2o.adaBoost(nlearners = 5, x = predictors, y = response, training_frame = data, seed = 1234, weak_learner = "GLM", weak_learner_params = list(max_iterations=3))
    expect_equal(is.null(h2o_adaboost), FALSE)
    h2o_adaboost <- h2o.adaBoost(nlearners = 5, x = predictors, y = response, training_frame = data, seed = 1234, weak_learner = "DEEP_LEARNING", weak_learner_params = list(nepochs=3, hidden=list(2,1,2)))
    expect_equal(is.null(h2o_adaboost), FALSE)
}

doTest("adaBoost: Smoke Test For Weak Learner Params - only that is pass through the API", test.adaBoost.smoke)
