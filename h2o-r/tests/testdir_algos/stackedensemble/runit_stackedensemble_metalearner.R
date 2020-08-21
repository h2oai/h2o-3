setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.metalearner.test <- function() {

  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.stackedEnsemble `metalearner_algorithm` works correctly
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


  # Check that not setting metalearner_algorithm still produces correct results
  # should be glm with non-negative weights
  stack0 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf))
  # Check that metalearner_algorithm default is GLM w/ non-negative
  expect_equal(stack0@parameters$metalearner_algorithm, "glm")
  expect_equal(stack0@allparameters$metalearner_algorithm, "glm")
  # Check that the metalearner is GLM w/ non-negative
  meta0 <- h2o.getModel(stack0@model$metalearner$name)
  expect_equal(meta0@algorithm, "glm")
  expect_equal(meta0@parameters$non_negative, TRUE)
  expect_equal(meta0@allparameters$non_negative, TRUE)


  # Train a stacked ensemble & check that metalearner_algorithm works
  stack1 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_algorithm = "gbm")
  # Check that metalearner_algorithm is a default GBM
  expect_equal(stack1@parameters$metalearner_algorithm, "gbm")
  expect_equal(stack1@allparameters$metalearner_algorithm, "gbm")
  # Check that the metalearner is default GBM
  meta1 <- h2o.getModel(stack1@model$metalearner$name)
  expect_equal(meta1@algorithm, "gbm")
  expect_equal(length(meta1@parameters), 7)  #no hyperparms are set (only the 5 basic: model_id, seed, distribution, x, y + 2 specified from AUTO: categorical_encoding, histogram_type)


  # Train a stacked ensemble & metalearner_algorithm "drf"; check that metalearner_algorithm works with CV
  stack2 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_nfolds = 3,
                                metalearner_algorithm = "drf")
  # Check that metalearner_algorithm is a default RF
  expect_equal(stack2@parameters$metalearner_algorithm, "drf")
  # Check that CV was performed
  expect_equal(stack2@allparameters$metalearner_nfolds, 3)
  meta2 <- h2o.getModel(stack2@model$metalearner$name)
  expect_equal(meta2@algorithm, "drf")
  expect_equal(meta2@allparameters$nfolds, 3)


  # Train a stacked ensemble & metalearner_algorithm "glm"
  stack3 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_algorithm = "glm")
  # Check that metalearner_algorithm is a default GLM
  expect_equal(stack3@parameters$metalearner_algorithm, "glm")
  meta3 <- h2o.getModel(stack3@model$metalearner$name)
  expect_equal(meta3@algorithm, "glm")


  # Train a stacked ensemble & metalearner_algorithm "deeplearning"
  stack4 <- h2o.stackedEnsemble(x = x,
                                y = y,
                                training_frame = train,
                                base_models = list(my_gbm, my_rf),
                                metalearner_algorithm = "deeplearning")
  # Check that metalearner_algorithm is a default DNN
  expect_equal(stack4@parameters$metalearner_algorithm, "deeplearning")
  meta4 <- h2o.getModel(stack4@model$metalearner$name)
  expect_equal(meta4@algorithm, "deeplearning")

  # PUBDEV-6955 - Add metalearner_model property containing the metalearner model
  expect_equal(stack4@model$metalearner_model@model_id, meta4@model_id)
}

doTest("Stacked Ensemble metalearner Test", stackedensemble.metalearner.test)
