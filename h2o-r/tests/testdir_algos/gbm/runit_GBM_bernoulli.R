setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.bernoulli <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  prostate.hex <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
  h2oTest.logInfo("Converting CAPSULE and RACE columns to factors...\n")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.hex$RACE <- as.factor(prostate.hex$RACE)
  h2oTest.logInfo("Summary of prostate.csv from H2O:\n")
  print(summary(prostate.hex))
  
  # Import csv data for R to use in comparison
  prostate.data <- read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data$RACE <- as.factor(prostate.data$RACE)
  h2oTest.logInfo("Summary of prostate.csv from R:\n")
  print(summary(prostate.data))
  
  # Train H2O GBM Model:
  ntrees <- 100
  h2oTest.logInfo(paste("H2O GBM with parameters:\ndistribution = 'bernoulli', ntrees = ", ntrees, ", max_depth = 5, min_rows = 10, learn_rate = 0.1\n", sep = ""))
  prostate.h2o <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, distribution = "bernoulli", ntrees = ntrees, max_depth = 5, min_rows = 10, learn_rate = 0.1)

  # Train R GBM Model: Using Gaussian distribution family for binary outcome OK... Also more comparable to H2O, which uses MSE
  h2oTest.logInfo("R GBM with same parameters and bag.fraction = 1\n")
  prostate.r <- gbm(CAPSULE ~ ., data = prostate.data[,-1], distribution = "bernoulli", 
                   n.trees = ntrees, interaction.depth = 5, n.minobsinnode = 10, shrinkage = 0.1, bag.fraction = 1)
  prostate.r.pred <- predict.gbm(prostate.r, prostate.data, n.trees = ntrees, type = "response")
  R.preds <- ifelse(prostate.r.pred < 0.5, 0, 1)
  # Break ties with a coin flip (see getPrediction in ModelUtils.java)
  if(any(prostate.r.pred == 0.5)) {
    R.preds[prostate.r.pred == 0.5] <- rbinom(1, 1, 0.5)
  }
  
  h2oTest.logInfo("Mean-squared Error by tree in H2O:\n")
  print(prostate.h2o@model$scoring_history)

  h2oTest.logInfo("Gaussian Deviance by tree in R (i.e. the per tree 'train error'):\n")
  print(prostate.r$train.err)
  
  RCM <- table(prostate.data$CAPSULE, R.preds)
  h2oTest.logInfo("R Confusion Matrix:")
  print(RCM)
  h2oTest.logInfo("H2O Confusion Matrix:")
  print(h2o.confusionMatrix(h2o.performance(prostate.h2o)))
  
  R.auc <- gbm.roc.area(prostate.data$CAPSULE,R.preds)
  h2oTest.logInfo(paste("R AUC:", R.auc, "\tH2O AUC:", h2o.auc(h2o.performance(prostate.h2o))))

  # PUBDEV-515
  f0 = log(mean(prostate.data$CAPSULE)/(1-mean(prostate.data$CAPSULE)))
  print(f0)
  print(prostate.h2o@model$init_f)
  expect_equal(prostate.h2o@model$init_f, f0, tolerance=1e-4) ## check the intercept term

  
}

h2oTest.doTest("GBM Test: prostate.csv with Bernoulli distribution", test.GBM.bernoulli)
