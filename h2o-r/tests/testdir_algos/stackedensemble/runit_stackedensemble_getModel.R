setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.getModel.test <- function() {

  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.stackedEnsemble `metalearner_algorithm` works correctly
  # 2) That h2o.stackedEnsemble `metalearner_algorithm` works in concert with `metalearner_nfolds`


  train <- h2o.uploadFile(locate("smalldata/testng/iris.csv"))
  test <- h2o.uploadFile(locate("smalldata/testng/iris.csv"))
  y <- "Species"
  x <- setdiff(names(train), y)
  train[,y] <- as.factor(train[,y])
  test[,y] <- as.factor(test[,y])
  nfolds <- 3  #number of folds for base learners

  # Train & Cross-validate a GBM
  my_gbm <- h2o.gbm(x = x,
                    y = y,
                    training_frame = train,
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


  # Check that not setting metalearner_algorithm still produces correct results
  # should be glm with non-negative weights
  stack0 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf))

  get_stack0 <- h2o.getModel(stack0@model_id)

  expected <- captureOutput(print(stack0))
  observed <- captureOutput(print(get_stack0))

  # Make sure we have model summary
  expect_true(any(grepl("Number of Base Models", expected, fixed = TRUE)))
  expect_equal(observed, expected)

}

doTest("Stacked Ensemble getModel Test", stackedensemble.getModel.test)
