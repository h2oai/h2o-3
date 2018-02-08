setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.custom.metalearner.test <- function() {

    # This test checks the following (for binomial classification):
    #
    # 1) That h2o.stackedEnsemble `metalearner_algorithm` and `metalearner_params` work correctly
    # 2) That h2o.stackedEnsemble `metalearner_algorithm` works in concert with `metalearner_nfolds`


    train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
    destination_frame = "higgs_train_5k")
    test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
    destination_frame = "higgs_test_5k")
    y <- "response"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])
    nfolds <- 3  #number of folds for base learners

    #Metalearner params for gbm, drf, glm, and deep deeplearning
    gbm_params <- list(ntrees=100, max_depth = 6)
    drf_params <- list(ntrees=100, max_depth = 6)
    glm_params <- list(alpha=0, lambda=0)
    dl_params  <- list(hidden=c(32,32,32), epochs = 20) ## small network, runs faster


    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "bernoulli",
                      ntrees = 10,
                      nfolds = nfolds,
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)

    # Train & Cross-validate a RF
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 10,
                              nfolds = nfolds,
                              keep_cross_validation_predictions = TRUE,
                              seed = 1)

    # Train a stacked ensemble & check that metalearner_algorithm/metalearner_params works
    print("SE with GBM metalearner and custom params")
    stack_gbm <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_algorithm = "gbm",
                                     metalearner_params = gbm_params)
    # Check that metalearner_algorithm is a default GBM
    expect_equal(stack_gbm@parameters$metalearner_algorithm, "gbm")
    expect_equal(stack_gbm@allparameters$metalearner_algorithm, "gbm")
    # Check that the metalearner is default GBM
    meta_gbm <- h2o.getModel(stack_gbm@model$metalearner$name)
    expect_equal(meta_gbm@algorithm, "gbm")
    expect_equal(meta_gbm@parameters$ntrees, 100)
    expect_equal(meta_gbm@parameters$max_depth, 6)


    # Train a stacked ensemble & metalearner_algorithm "drf"; check that metalearner_algorithm works with CV
    print("SE with DRF metalearner and custom params")
    stack_drf <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_nfolds = 3,
                                     metalearner_algorithm = "drf",
                                     metalearner_params = drf_params)
    # Check that metalearner_algorithm is a default RF
    expect_equal(stack_drf@parameters$metalearner_algorithm, "drf")
    # Check that CV was performed
    expect_equal(stack_drf@allparameters$metalearner_nfolds, 3)
    meta_drf <- h2o.getModel(stack_drf@model$metalearner$name)
    expect_equal(meta_drf@algorithm, "drf")
    expect_equal(meta_drf@allparameters$nfolds, 3)
    expect_equal(meta_drf@parameters$ntrees, 100)
    expect_equal(meta_drf@parameters$max_depth, 6)


    # Train a stacked ensemble & metalearner_algorithm "glm"
    print("SE with GLM metalearner and custom params")
    stack_glm <- h2o.stackedEnsemble(x = x,
                                     y = y,
                                     training_frame = train,
                                     base_models = list(my_gbm, my_rf),
                                     metalearner_algorithm = "glm",
                                     metalearner_params = glm_params)
    # Check that metalearner_algorithm is a default GLM
    expect_equal(stack_glm@parameters$metalearner_algorithm, "glm")
    meta_glm <- h2o.getModel(stack_glm@model$metalearner$name)
    expect_equal(meta_glm@algorithm, "glm")
    expect_equal(meta_glm@parameters$alpha, 0)
    expect_equal(meta_glm@parameters$lambda, 0)


    # Train a stacked ensemble & metalearner_algorithm "deeplearning"
    print("SE with DL metalearner and custom params")
    stack_deeplearning <- h2o.stackedEnsemble(x = x,
                                              y = y,
                                              training_frame = train,
                                              base_models = list(my_gbm, my_rf),
                                              metalearner_algorithm = "deeplearning",
                                              metalearner_params = dl_params)
    # Check that metalearner_algorithm is a default DNN
    expect_equal(stack_deeplearning@parameters$metalearner_algorithm, "deeplearning")
    meta_dl <- h2o.getModel(stack_deeplearning@model$metalearner$name)
    expect_equal(meta_dl@algorithm, "deeplearning")
    expect_equal(meta_dl@parameters$hidden, c(32,32,32))
    expect_equal(meta_dl@parameters$epochs, 20)

}

doTest("Stacked Ensemble Custom Metalearner Test", stackedensemble.custom.metalearner.test)
