setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.binary.test <- function() {
    # Import a sample binary outcome train/test set into H2O
    train <- h2o.uploadFile(locate("smalldata/higgs/higgs_train_10k.csv"), destination_frame = "higgs_train_10k")
    test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"), destination_frame = "higgs_test_5k")

    # Identify predictors and response
    y <- "response"
    x <- setdiff(names(train), y)

    # For binary classification, response should be a factor
    train[,y] <- as.factor(train[,y])
    test[,y] <- as.factor(test[,y])

    # Number of CV folds (to generate level-one data for stacking)
    nfolds <- 5

    # 1. Generate a 2-model ensemble (GBM + RF)

    # Train & Cross-validate a GBM
    my_gbm <- h2o.gbm(x = x,
                      y = y,
                      training_frame = train,
                      distribution = "bernoulli",
                      ntrees = 10,
                      max_depth = 3,
                      min_rows = 2,
                      learn_rate = 0.2,
                      nfolds = nfolds,
                      fold_assignment = "Modulo",
                      keep_cross_validation_predictions = TRUE,
                      seed = 1)

    # Train & Cross-validate a RF
    my_rf <- h2o.randomForest(x = x,
                              y = y,
                              training_frame = train,
                              ntrees = 50,
                              nfolds = nfolds,
                              fold_assignment = "Modulo",
                              keep_cross_validation_predictions = TRUE,
                              seed = 1)

    # Train a stacked ensemble using the GBM and RF above
    ensemble <- h2o.stackedEnsemble(x = x,
                                    y = y,
                                    training_frame = train,
                                    model_id = "my_ensemble_binomial",
                                    base_models = list(my_gbm@model_id, my_rf@model_id))


    #Predict in ensemble in R client
    preds_r <- h2o.predict(ensemble,test)

    #Load binary model and predict
    bin_model <- h2o.loadModel(locate("smalldata/binarymodels/stackedensemble/ensemble_higgs"))
    preds_bin <- h2o.predict(bin_model,test)

    #Predictions from model in R and binary model should be the same
    expect_equal(as.data.frame(preds_r),as.data.frame(preds_bin))
}

doTest("Stacked Ensemble Binary Model Export", stackedensemble.binary.test)