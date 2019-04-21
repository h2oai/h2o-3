setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_4778 = function() {
    expect_true(h2o.xgboost.available())

    train <- h2o.importFile(locate("smalldata/iris/iris_train.csv"))
    test <- h2o.importFile(locate("smalldata/iris/iris_test.csv"))
    test$species <- NULL # remove response from the tet dataset

    y <- "species"
    x <- setdiff(names(train), y)

    # 1. Train multinomial xgboost model
    xgb_multi <- h2o.xgboost(x = x, y = y, training_frame = train, distribution = "AUTO", ntrees = 2, seed = 1)
    gbm_multi <- h2o.gbm(x = x, y = y, training_frame = train, distribution = "AUTO", ntrees = 2, seed = 1)

    multi_predict_xgb <- h2o.predict(xgb_multi, test)
    expect_equal(nrow(multi_predict_xgb), nrow(test))

    multi_predict_gbm <- h2o.predict(gbm_multi, test)
    expect_equal(colnames(multi_predict_xgb), colnames(multi_predict_gbm)) # Column names are the same

    # 2. Train binomial model
    train_bin <- train
    train_bin$species <- as.factor(train$species == "Iris-setosa")
    test_bin <- test

    xgb_bin <- h2o.xgboost(x = x, y = y, training_frame = train_bin, distribution = "AUTO", ntrees = 2, seed = 1)
    gbm_bin <- h2o.gbm(x = x, y = y, training_frame = train_bin, distribution = "AUTO", ntrees = 2, seed = 1)

    bin_predict_xgb <- h2o.predict(xgb_bin, test_bin)
    expect_equal(nrow(bin_predict_xgb), nrow(test_bin))

    bin_predict_gbm <- h2o.predict(gbm_bin, test)
    expect_equal(colnames(bin_predict_xgb), colnames(bin_predict_gbm)) # Column names are the same

    # 3. Train regression model
    train_reg <- train
    train_reg$species <- (train$species == "Iris-setosa") * pi
    test_reg <- test

    xgb_reg <- h2o.xgboost(x = x, y = y, training_frame = train_reg, distribution = "AUTO", ntrees = 2, seed = 1)
    gbm_reg <- h2o.gbm(x = x, y = y, training_frame = train_reg, distribution = "AUTO", ntrees = 2, seed = 1)

    reg_predict_xgb <- h2o.predict(xgb_reg, test_reg)
    expect_equal(nrow(reg_predict_xgb), nrow(test_reg))

    reg_predict_gbm <- h2o.predict(gbm_reg, test)
    expect_equal(colnames(reg_predict_xgb), colnames(reg_predict_gbm)) # Column names are the same
}

doTest("h2o.predict works if response is missing & prediction frame columns have correct names", test.pubdev_4778)
