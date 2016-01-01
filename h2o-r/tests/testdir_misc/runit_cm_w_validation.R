setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.cm.valid <- function() {
  h2oTest.logInfo("Creating a binomial GBM model...")
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")
  pros.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train, validation_frame = pros.test)
  h2oTest.logInfo("Creating a multinomial GBM model...")
  iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")
  iris.test <- h2o.assign(iris.hex[i.sid <= .2, ], "iris.test")
  iris.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train, validation_frame = iris.test)

  h2oTest.logInfo("Basic Implementations...")
  cm_bin_basic <- h2o.confusionMatrix(pros.gbm)
  cm_mul_basic <- h2o.confusionMatrix(iris.gbm)
  print(cm_bin_basic)
  print(cm_mul_basic)
  # valid = FALSE ==> training_data => basic impelementation
  cm_false_pros <- h2o.confusionMatrix(pros.gbm, valid = FALSE)
  cm_false_iris <- h2o.confusionMatrix(iris.gbm, valid = FALSE)
  print(cm_false_pros)
  print(cm_false_iris)
  expect_equal(cm_false_pros, cm_bin_basic)
  expect_equal(cm_false_iris, cm_mul_basic)

  h2oTest.logInfo("Using valid = TRUE...")
  cm_bin_valid <- h2o.confusionMatrix(pros.gbm, valid = TRUE)
  cm_mul_valid <- h2o.confusionMatrix(iris.gbm, valid = TRUE)
  print(cm_bin_valid)
  print(cm_mul_valid)

  h2oTest.logInfo("Using newdata...")
  cm_bin_newdat <- h2o.confusionMatrix(pros.gbm, newdata = pros.test)
  cm_mul_newdat <- h2o.confusionMatrix(iris.gbm, newdata = iris.test)
  print(cm_bin_newdat)
  print(cm_mul_newdat)

  expect_equal(cm_bin_valid, cm_bin_newdat)
  expect_equal(cm_mul_valid, cm_mul_newdat)

  h2oTest.logInfo("Thresholds by various max criteria thresholds...")
  maxes <- pros.gbm@model$training_metrics@metrics$max_criteria_and_metric_scores$threshold[1:7]
  multiple_cm <- h2o.confusionMatrix(pros.gbm, thresholds = maxes)

  h2oTest.logInfo("Negative testcases...")
  expect_error(h2o.confusionMatrix(pros.gbm, valid = TRUE, newdata = pros.test))
  expect_error(h2o.confusionMatrix(iris.glm, thresholds = 0.5))
  
}

h2oTest.doTest("Testing h2o.confusionMatrix on a model with validation frame", test.cm.valid)
