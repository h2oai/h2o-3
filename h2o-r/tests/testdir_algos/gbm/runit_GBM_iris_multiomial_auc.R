setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.GBM.iris.multinomial.auc <- function() {
    prostate <- h2o.importFile(path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/prostate/prostate.csv")

    # Split dataset giving the training dataset 75% of the data
    prostate_split <- h2o.splitFrame(data = prostate, ratios = 0.75)

    response_col = "GLEASON"

    # Create a training set from the 1st dataset in the split
    train.hex <- prostate_split[[1]]
    train.hex[, response_col] = as.factor(train.hex[, response_col])

    # Create a testing set from the 2nd dataset in the split
    test.hex <- prostate_split[[2]]
    test.hex[, response_col] = as.factor(test.hex[, response_col])

    predictors = c("RACE", "AGE", "PSA", "DPROS", "CAPSULE", "VOL", "DCAPS")

    # Build GBM model with cv
    iris.gbm <- h2o.gbm(y=response_col, x=predictors, distribution="multinomial", training_frame=train.hex, validation_frame=test.hex, ntrees=5, max_depth=2, min_rows=20, nfold=3)

    # Check aucpr is not in performance table
    print(iris.gbm@model$cross_validation_metrics_summary)
    expect_false("aucpr" %in% row.names(iris.gbm@model$cross_validation_metrics_summary))
    expect_true("pr_auc" %in% row.names(iris.gbm@model$cross_validation_metrics_summary))
}

doTest("GBM test checkpoint on iris", test.GBM.iris.multinomial.auc)
