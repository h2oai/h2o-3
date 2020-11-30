setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.metalearner.seed.test <- function() {

    print("Reading in data")
    train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
    destination_frame = "higgs_train_5k")
    test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
    destination_frame = "higgs_test_5k")
    y <- "response"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])
    nfolds <- 3  #number of folds for base learners

    print("Training GBM")
    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "bernoulli",
                      ntrees = 10,
                      nfolds = nfolds,
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)

    print("Training DRF")
    # Train & Cross-validate a RF
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 10,
                              nfolds = nfolds,
                              keep_cross_validation_predictions = TRUE,
                              seed = 1)

    #GBM metalearner params
    gbm_params <- list(sample_rate=0.3, col_sample_rate=0.3)

    print("Training 2 SE models with same metalearner seeds")
    stack0 <- h2o.stackedEnsemble(x = x,
                                  y = y,
                                  training_frame = train,
                                  base_models = list(my_gbm, my_rf),
                                  metalearner_algorithm = "gbm",
                                  metalearner_params = gbm_params,
                                  seed = 55555)

    stack1 <- h2o.stackedEnsemble(x = x,
                                  y = y,
                                  training_frame = train,
                                  base_models = list(my_gbm, my_rf),
                                  metalearner_algorithm = "gbm",
                                  metalearner_params = gbm_params,
                                  seed = 55555)

    # Seed should be retrieved correctly from both SE and MetaLearner
    expect_equal(as.numeric(stack0@parameters$seed), 55555)
    expect_equal(as.numeric(stack0@model$metalearner_model@parameters$seed), 55555)

    #RMSE should match for each metalearner since the same seed is used
    meta0 <- h2o.getModel(stack0@model$metalearner$name)
    meta1 <- h2o.getModel(stack1@model$metalearner$name)
    expect_true(h2o.rmse(meta0) - h2o.rmse(meta1) == 0)

    print("Training 2 SE models with different metalearner seeds")
    stack2 <- h2o.stackedEnsemble(x = x,
                                  y = y,
                                  training_frame = train,
                                  base_models = list(my_gbm, my_rf),
                                  metalearner_algorithm = "gbm",
                                  metalearner_params = gbm_params,
                                  seed = 55555)

    stack3 <- h2o.stackedEnsemble(x = x,
                                  y = y,
                                  training_frame = train,
                                  base_models = list(my_gbm, my_rf),
                                  metalearner_algorithm = "gbm",
                                  metalearner_params = gbm_params,
                                  seed = 98765)
    #RMSE should NOT match for each metalearner since the same seed is NOT used
    meta2 <- h2o.getModel(stack2@model$metalearner$name)
    meta3 <- h2o.getModel(stack3@model$metalearner$name)
    expect_true(h2o.rmse(meta2) - h2o.rmse(meta3) != 0)

}

doTest("Stacked Ensemble Metalearner Seed Test", stackedensemble.metalearner.seed.test)
