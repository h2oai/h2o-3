setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.validation_frame.test <- function() {
  
  # This test checks the following (for binomial classification):
  # 
  # 1) That passing in a validation_frame to h2o.stackedEnsemble does something (validation metrics exist)
  # 2) It should hopefully produce a better model (in the metalearning step)
     
  df <- h2o.uploadFile(locate("smalldata/higgs/higgs_train_5k.csv"), 
                          destination_frame = "higgs_train_5k")
  test <- h2o.uploadFile(locate("smalldata/higgs/higgs_test_5k.csv"), 
                          destination_frame = "higgs_test_5k")
  y <- "response"
  x <- setdiff(names(df), y)
  df[,y] <- as.factor(df[,y])
  test[,y] <- as.factor(test[,y])
  nfolds <- 5
  
  # Split off a validation_frame
  ss <- h2o.splitFrame(df, seed = 1)
  train <- ss[[1]]
  valid <- ss[[2]]
  
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
  
  
  # Train a stacked ensemble & check that validation metrics are missing
  stack1 <- h2o.stackedEnsemble(x = x, 
                                y = y, 
                                training_frame = train,
                                base_models = list(my_gbm, my_rf))
  expect_equal(inherits(h2o.performance(stack1, valid = TRUE), "H2OBinomialMetrics"), FALSE)
  
  # Train a stacked ensemble with a validation_frame & check that validation metrics exist & are correct type
  stack2 <- h2o.stackedEnsemble(x = x, 
                                y = y, 
                                training_frame = train,
                                validation_frame = valid,
                                base_models = list(my_gbm, my_rf))
  expect_equal(inherits(h2o.performance(stack2, valid = TRUE), "H2OBinomialMetrics"), TRUE)
  expect_equal(class(h2o.auc(stack2, valid = TRUE)), "numeric")
  
  
  # Compare test AUC (ensemble with validation_frame should not be worse)
  perf1 <- h2o.performance(model = stack1, newdata = test)
  perf2 <- h2o.performance(model = stack2, newdata = test)
  expect_true(h2o.auc(perf1) >= h2o.auc(perf2))
  
}

doTest("Stacked Ensemble validation_frame Test", stackedensemble.validation_frame.test)
