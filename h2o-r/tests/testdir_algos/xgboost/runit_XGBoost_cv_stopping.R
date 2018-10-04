setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.xgboost.cv_stopping <- function() {
    expect_true(h2o.xgboost.available())

    iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"))

    xgb.model <- h2o.xgboost(y = "class", training_frame = iris.hex, ntrees = 100, nfold = 5, seed = 123,
                             stopping_rounds = 5, score_tree_interval = 5, keep_cross_validation_models=T)

    ntrees <- sapply(1:5, function(i) {
        cv.name <- xgb.model@model$cross_validation_models[[i]]$name
        cv.model <- h2o.getModel(cv.name)
        cv.model@model$model_summary$number_of_trees
    })

    expect_equal(round(mean(ntrees)), xgb.model@model$model_summary$number_of_trees)
}

doTest("Number of trees in XGBoost main model should be derived from CV models", test.xgboost.cv_stopping)
