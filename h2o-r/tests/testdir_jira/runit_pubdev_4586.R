setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_4586 = function() {
    expect_true(h2o.xgboost.available())

    train <- h2o.importFile(locate("smalldata/iris/iris_train.csv"))
    test <- h2o.importFile(locate("smalldata/iris/iris_test.csv"))

    y <- "species"
    x <- setdiff(names(train), y)

    # Train XGB-GBM
    xgb <- h2o.xgboost(x = x,
                       y = y,
                       training_frame = train,
                       distribution = "AUTO",
                       ntrees = 2,
                       seed = 1)
    mm.train <- h2o.performance(xgb, newdata = train)
    expect_true(!is.null(mm.train))
    mm.test <- h2o.performance(xgb, newdata = test)
    expect_true(!is.null(mm.test))
}

doTest("h2o.performance function doesn't work on XGBoost models", test.pubdev_4586)
