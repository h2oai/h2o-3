library(h2o)
h2o.init()

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_4585 <- function() {
    # Import a sample binary outcome train/test set into H2O
    train <- h2o.importFile(normalizePath(locate("smalldata/higgs/higgs_train_10k.csv")))

    # Identify predictors and response
    y <- "response"
    x <- setdiff(names(train), y)

    # For binary classification, response should be a factor
    train[,y] <- as.factor(train[,y])


    # Train XGB-GBM
    xgb <- h2o.xgboost(x = x,
                       y = y,
                       training_frame = train,
                       distribution = "bernoulli",
                       seed = 1)

    # From Erin's original version:
    # This should also not be broken.  Will file a separate bug:
    #auc <- h2o.auc(h2o.performance(xgb, newdata = test))
    #> h2o.performance(xgb, newdata = test)
    #Error in Filter(function(mm) { : subscript out of bounds

    test <- h2o.importFile(normalizePath(locate("smalldata/higgs/higgs_test_5k.csv")))

    test[,y] <- as.factor(test[,y])
    pred_orig <- h2o.predict(xgb, newdata = test)
    print("pred_orig[1,3]: ")
    print(pred_orig[1,3])
    pred_orig_1_3 <- pred_orig[1,3]

    mfile <- h2o.saveModel(xgb, path = tempdir())
    rm("xgb", "train", "test")


    print("about to start up / connect to a different instance")
    h2o.init(ip="localhost", port=54319)

    h2o.ls()
    print("YO! connected to a new instance!")

    # Load xgb into the new instance
    xgb <- h2o.loadModel(path = mfile)
    xgb

    test <- h2o.importFile(normalizePath(locate("smalldata/higgs/higgs_test_5k.csv")))
    test[,y] <- as.factor(test[,y])


    h2o.performance(xgb, newdata = test)

    pred_reloaded <- h2o.predict(xgb, newdata = test)
    print("pred_reloaded[1,3]: ")
    print(pred_reloaded[1,3])
    expect_true(abs(pred_orig_1_3 - pred_reloaded[1,3]) < 1e-5)

    # we don't want old instances lying around on the machine, especially with old code versions
    h2o.shutdown(prompt = FALSE)
}

doTest("PUBDEV-4585: XGBoost binary save/load", test.pubdev_4585)
