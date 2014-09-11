setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.GBM.bernoulli <- function(conn) {
  Log.info("Importing prostate.csv data...\n")
  prostate.hex <- h2o.uploadFile(conn, locate("smalldata/logreg/prostate.csv"), key="prostate.hex")
  Log.info("Converting CAPSULE and RACE columns to factors...\n")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.hex$RACE <- as.factor(prostate.hex$RACE)
  Log.info("Summary of prostate.csv from H2O:\n")
  print(summary(prostate.hex))
  
  # Import csv data for R to use in comparison
  prostate.data <- read.csv(locate("smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data$RACE <- as.factor(prostate.data$RACE)
  Log.info("Summary of prostate.csv from R:\n")
  print(summary(prostate.data))
  
  # Train H2O GBM Model:
  n.trees <<- 100
  Log.info(paste("H2O GBM with parameters:\ndistribution = 'bernoulli', ntrees = ", n.trees, ", max_depth = 5, min_rows = 10, learn_rate = 0.1\n", sep = ""))
  prostate.h2o <- h2o.gbm(x = 3:9, y = "CAPSULE", data = prostate.hex, distribution = "bernoulli",
                          n.trees = n.trees, interaction.depth = 5, n.minobsinnode = 10, shrinkage = 0.1)
  
  # Train R GBM Model: Using Gaussian loss function for binary outcome OK... Also more comparable to H2O, which uses MSE
  Log.info("R GBM with same parameters and bag.fraction = 1\n")
  prostate.r <- gbm(CAPSULE ~ ., data = prostate.data[,-1], distribution = "bernoulli", 
                   n.trees = n.trees, interaction.depth = 5, n.minobsinnode = 10, shrinkage = 0.1, bag.fraction = 1)
  prostate.r.pred <- predict.gbm(prostate.r, prostate.data, n.trees = n.trees, type = "response")
  R.preds <- ifelse(prostate.r.pred < 0.5, 0, 1)
  # Break ties with a coin flip (see getPrediction in ModelUtils.java)
  if(any(prostate.r.pred == 0.5)) {
    R.preds[prostate.r.pred == 0.5] = rbinom(1, 1, 0.5)
  }
  
  Log.info("Mean-squared Error by tree in H2O:\n")
  print(prostate.h2o@model$err)
  Log.info("Gaussian Deviance by tree in R (i.e. the per tree 'train error'):\n")
  print(prostate.r$train.err)
  
  RCM <- table(prostate.data$CAPSULE, R.preds)
  Log.info("R Confusion Matrix:")
  print(RCM)
  Log.info("H2O Confusion Matrix:")
  print(prostate.h2o@model$confusion)
  
  R.auc = gbm.roc.area(prostate.data$CAPSULE,R.preds)
  Log.info(paste("R AUC:", R.auc, "\tH2O AUC:", prostate.h2o@model$auc))
  testEnd()
}

doTest("GBM Test: prostate.csv with Bernoulli distribution", test.GBM.bernoulli)