setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.nfolds.test <- function() {

  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.stackedEnsemble `metalearner_nfolds` works correctly
  # 2) That h2o.stackedEnsemble `metalearner_fold_assignment` works correctly
  # 3) That Stacked Ensemble cross-validation metrics are correctly copied from metalearner

  train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
                          destination_frame = "higgs_train_5k")
  test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
                         destination_frame = "higgs_test_5k")
  # Add a fold_column
  fold_column <- "fold_id"
  train[,fold_column] <- as.h2o(data.frame(rep(seq(1:3), 2000)[1:nrow(train)]))  #three folds
  y <- "response"
  x <- setdiff(names(train), c(y, fold_column))
  train[,y] <- as.factor(train[,y])
  test[,y] <- as.factor(test[,y])
  nfolds <- 3  #number of folds for base learners

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


  # Check that not setting nfolds still produces correct results
  stack0 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf))
  # Check that metalearner_nfolds is correctly stored in model output
  expect_equal(stack0@parameters$metalearner_nfolds, NULL)
  expect_equal(stack0@allparameters$metalearner_nfolds, 0)
  # Check that the metalearner was cross-validated with the correct number of folds
  meta0 <- h2o.getModel(stack0@model$metalearner$name)
  expect_equal(meta0@parameters$nfolds, NULL)
  expect_equal(meta0@allparameters$nfolds, 0)


  # Train a stacked ensemble & check that metalearner_nfolds works
  # Also test that the xval metrics from metalearner & ensemble are equal
  stack1 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_nfolds = 3)
  # Check that metalearner_nfolds is correctly stored in model output
  expect_equal(stack1@parameters$metalearner_nfolds, 3)
  expect_equal(stack1@allparameters$metalearner_nfolds, 3)
  # Check that the metalearner was cross-validated with the correct number of folds
  meta1 <- h2o.getModel(stack1@model$metalearner$name)
  expect_equal(meta1@parameters$nfolds, 3)
  expect_equal(meta1@allparameters$nfolds, 3)
  # Check that metalearner fold_assignment is NULL/"AUTO"
  expect_equal(meta1@parameters$fold_assignment, NULL)
  expect_equal(meta1@allparameters$fold_assignment, "AUTO")
  # Check that validation metrics are NULL
  expect_equal(h2o.mse(stack1, valid = TRUE), NULL)
  # Check that xval metrics from metalearner and ensemble are equal (use mse as proxy)
  expect_equal(h2o.mse(stack1, xval = TRUE), h2o.mse(meta1, xval = TRUE))


  # Train a new ensmeble, also passing a validation frame
  ss <- h2o.splitFrame(test, ratios = 0.5, seed = 1)
  stack2 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                validation_frame = ss[[1]],
                                base_models = list(my_gbm, my_rf),
                                metalearner_nfolds = 3)
  # Check that valid & xval metrics from metalearner and ensemble are equal (use mse as proxy)
  meta2 <- h2o.getModel(stack2@model$metalearner$name)
  expect_equal(h2o.mse(stack2, valid = TRUE), h2o.mse(meta2, valid = TRUE))
  expect_equal(h2o.mse(stack2, xval = TRUE), h2o.mse(meta2, xval = TRUE))

  # The xval metrics are different if you use a validation_frame
  # Is this becuase validation_frame is being used to train a better metalearner?
  # But I thought that GLM was not doing early stopping... need to double check that
  #expect_equal(h2o.mse(stack1, xval = TRUE), h2o.mse(stack2, xval = TRUE))


  # Check that metalearner_fold_assignment works
  stack3 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_nfolds = 3,
                                metalearner_fold_assignment = "Modulo")
  # Check that metalearner_fold_assignment is correctly stored in model output
  expect_equal(stack3@parameters$metalearner_fold_assignment, "Modulo")
  expect_equal(stack3@allparameters$metalearner_fold_assignment, "Modulo")
  # Check that metalearner_fold_assignment is passed through to metalearner
  meta3 <- h2o.getModel(stack3@model$metalearner$name)
  expect_equal(meta3@parameters$fold_assignment, "Modulo")
  expect_equal(meta3@allparameters$fold_assignment, "Modulo")


  # Check that metalearner_fold_column works
  stack4 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_fold_column = fold_column,
                                metalearner_params = list(keep_cross_validation_models=T))
  # Check that metalearner_fold_column is correctly stored in model output
  expect_equal(stack4@parameters$metalearner_fold_column$column_name, fold_column)
  expect_equal(stack4@allparameters$metalearner_fold_column$column_name, fold_column)
  # Check that metalearner_fold_column is passed through to metalearner
  meta4 <- h2o.getModel(stack4@model$metalearner$name)
  expect_equal(meta4@parameters$fold_column$column_name, fold_column)
  expect_equal(meta4@allparameters$fold_column$column_name, fold_column)
  expect_equal(meta4@allparameters$nfolds, 0)
  expect_equal(length(meta4@model$cross_validation_models), 3)

}

doTest("Stacked Ensemble nfolds & fold_assignment Test", stackedensemble.nfolds.test)
