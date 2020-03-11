setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# PUBDEV-6787
stackedensemble.base_models.are.easily.accessible.test <- function() {
    # This test checks the following (for binomial classification):
    #
    # 1) That passing in a list of models for base_models works.
    # 2) That passing in a list of models and model_ids results in the
    #    same stacked ensemble.

    train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame = "higgs_train_5k")
    y <- "response"
    x <- setdiff(names(train), y)
    train[,y] <- as.factor(train[,y])
    nfolds <- 5

    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "bernoulli",
                      ntrees = 10,
                      nfolds = nfolds,
                      fold_assignment = "Modulo",
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)

    # Train & Cross-validate a RF
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 10,
                              nfolds = nfolds,
                              fold_assignment = "Modulo",
                              keep_cross_validation_predictions = TRUE,
                              seed = 1)

    # Train a stacked ensemble using the GBM and RF above
    stack <- h2o.stackedEnsemble(x = x,
                                 y = y,
                                 training_frame = train,
                                 model_id = "my_ensemble_binomial",
                                 base_models = list(my_gbm@model_id, my_rf@model_id))

    retriveved_stack <- h2o.getModel(stack@model_id)

    expect_equal(stack@model$base_models, unlist(lapply(stack@parameters$base_models,
                                                        function (base_model) base_model$name)))
    expect_equal(stack@model$base_models, retriveved_stack@model$base_models)
    # Ensure that we have only model ids here not some nested list
    expect_true(is.character(stack@model$base_models) && length(stack@model$base_models) == 2)
}

doTest("Stacked Ensemble base_models are in model$base_models Test", stackedensemble.base_models.are.easily.accessible.test)
