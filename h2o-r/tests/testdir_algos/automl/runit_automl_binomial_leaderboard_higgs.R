setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.leaderboard.test <- function() {

  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.automl leaderboard metrics are accurate

  train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"),
                          destination_frame = "higgs_train_5k")
  test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"),
                         destination_frame = "higgs_test_5k")

  y <- "response"
  train[,y] <- as.factor(train[,y])
  test[,y] <- as.factor(test[,y])

  # Train AutoML with just a training_frame & leaderboard_frame
  aml <- h2o.automl(y = y,
                    training_frame = train,
                    leaderboard_frame = test,
                    max_runtime_secs = 30)

  # Get test set AUC from leaderboard vs h2o.performance() and check that it matches
  auc_aml_leaderboard_test <- as.numeric(aml@leaderboard[1,2])  #metric column in first/top row
  perf_aml_test <- h2o.performance(model = aml@leader, newdata = test)
  auc_aml_test <- h2o.auc(perf_aml_test)


  # Check that stack perf is better (bigger) than the best (biggest) base learner perf:
  print(sprintf("Leaderboard Test Set AUC:  %s", auc_aml_leaderboard_test))
  print(sprintf("h2o.performance() Test Set AUC:  %s", auc_aml_test))
  expect_equal(auc_aml_leaderboard_test, expected = auc_aml_test, tolerance = 0.000001)
}

doTest("AutoML Leaderboard Test", automl.leaderboard.test)
