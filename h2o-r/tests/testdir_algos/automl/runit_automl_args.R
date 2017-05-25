setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.args.test <- function() {
  
  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.automl executes w/o errors
  
  train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
                          destination_frame = "higgs_train_5k")
  test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
                         destination_frame = "higgs_test_5k")
  ss <- h2o.splitFrame(test, seed = 1)
  valid <- ss[[1]]
  test <- ss[[1]] 
  
  y <- "response"
  x <- setdiff(names(train), y)
  train[,y] <- as.factor(train[,y])
  test[,y] <- as.factor(test[,y])
  
  
  # TO DO: Change this to use different project_name: https://0xdata.atlassian.net/browse/PUBDEV-4420

  # Check max_models
  aml0 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     max_runtime_secs = 10,
                     max_models = 1)
  expect_equal(nrow(aml0@leaderboard), 2)
  

  # Try without an x
  aml1 <- h2o.automl(y = y, 
                     training_frame = train,
                     max_runtime_secs = 10)
  
  # Single training frame
  aml2 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     max_runtime_secs = 10)
  
  # Training & validation frame
  aml3 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     validation_frame = valid,
                     max_runtime_secs = 10)
  
  # Training & leaderboard frame
  aml4 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     leaderboard_frame = test,
                     max_runtime_secs = 10)
  
  # Training, validaion & leaderboard frame
  aml5 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     validation_frame = valid,
                     leaderboard_frame = test,
                     max_runtime_secs = 10)
  
  # Early stopping
  aml6 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     validation_frame = valid,
                     leaderboard_frame = test,
                     max_runtime_secs = 10,
                     stopping_metric = "AUC",
                     stopping_tolerance = 0.001,
                     stopping_rounds = 3)
  
}

doTest("AutoML Args Test", automl.args.test)
