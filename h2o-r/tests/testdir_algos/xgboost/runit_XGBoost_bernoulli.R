setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.bernoulli <- function() {
  Log.info("Importing prostate.csv data...\n")
  prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame="prostate.hex")
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
  
  # Train H2O XGBoost Model:
  ntrees <<- 100
  distribution <<- "bernoulli"
  min_rows <<- 10
  learn_rate <<- 0.7
  max_depth <<- 5
  prostate.h2o <- h2o.xgboost(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, distribution = distribution, ntrees = ntrees, max_depth = max_depth, min_rows = min_rows, learn_rate = learn_rate)
  model.params = h2o::getParms(prostate.h2o)
  expect_equal(model.params$ntrees, ntrees)
  expect_equal(model.params$max_depth, max_depth)
  expect_equal(model.params$learn_rate, learn_rate)
  expect_equal(model.params$min_rows, min_rows)
  expect_equal(model.params$distribution, distribution)
}

doTest("XGBoost Test: prostate.csv with Bernoulli distribution", test.XGBoost.bernoulli)
