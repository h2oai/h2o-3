setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.leaderboard_sort_metric.test <- function() {
  
  # Test that sort_metric is working
  
  # Binomial:
  fr1 <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  fr1["CAPSULE"] <- as.factor(fr1["CAPSULE"])
  aml1 <- h2o.automl(y = 2, training_frame = fr1, max_models = 2,
                     project_name = "r_lbsm_test_aml1",
                     sort_metric = "AUC")  #currently returns unsorted lb for "AUC"
  aml1@leaderboard
  # check that correct leaderboard columns exist
  expect_equal(names(aml1@leaderboard), c("model_id", "auc", "logloss", "mean_per_class_error", "rmse", "mse"))
  # check that auc col is sorted already
  auc_col <- as.vector(aml1@leaderboard[,"auc"])
  expect_equal(identical(auc_col, sort(auc_col, decreasing = TRUE)), TRUE)

  # add to this AutoML run, using new sort_metric
  aml1 <- h2o.automl(y = 2, training_frame = fr1, max_models = 2,
                     project_name = "r_lbsm_test_aml1",
                     sort_metric = "logloss")
  aml1@leaderboard
  # check that auc col is sorted already
  logloss_col <- as.vector(aml1@leaderboard[,"logloss"])
  expect_equal(identical(logloss_col, sort(logloss_col, decreasing = FALSE)), TRUE)

  # new AutoML run, sort_metric AUTO (check sorting by auc)
  aml1 <- h2o.automl(y = 2, training_frame = fr1, max_models = 2,
                     project_name = "r_lbsm_test_aml1_auto")
  aml1@leaderboard
  # check that leaderboard is sorted by auc
  auc_col <- as.vector(aml1@leaderboard[,"auc"])
  expect_equal(identical(auc_col, sort(auc_col, decreasing = TRUE)), TRUE)


  # Regression:
  fr2 <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"))
  aml2 <- h2o.automl(y = 'runoffnew', training_frame = fr2, max_models = 2,
                     project_name = "r_lbsm_test_aml2",
                     sort_metric = "RMSE")
  aml2@leaderboard
  expect_equal(names(aml2@leaderboard), c("model_id", "mean_residual_deviance", "rmse", "mse", "mae", "rmsle"))
  # check that rmse col is sorted already
  rmse_col <- as.vector(aml2@leaderboard[,"rmse"])
  expect_equal(identical(rmse_col, sort(rmse_col, decreasing = FALSE)), TRUE)

  # new AutoML run, sort_metric AUTO (check sorting by mean_residual_deviance)
  aml2 <- h2o.automl(y = 'runoffnew', training_frame = fr2, max_models = 2,
                     project_name = "r_lbsm_test_aml2_auto")
  aml2@leaderboard
  # check that leaderboard is sorted by mean_residual_deviance
  mrd_col <- as.vector(aml2@leaderboard[,"mean_residual_deviance"])
  expect_equal(identical(mrd_col, sort(mrd_col, decreasing = FALSE)), TRUE)
  
  
  # Multinomial:
  fr3 <- as.h2o(iris)
  aml3 <- h2o.automl(y = 5, training_frame = fr3, max_models = 2,
                     project_name = "r_lbsm_test_aml3",
                     sort_metric = "MSE")
  aml3@leaderboard
  expect_equal(names(aml3@leaderboard),c("model_id", "mean_per_class_error", "logloss", "rmse", "mse"))
  # check that mse col is sorted already
  mse_col <- as.vector(aml3@leaderboard[,"mse"])
  expect_equal(identical(mse_col, sort(mse_col, decreasing = FALSE)), TRUE)
  
  # new AutoML run, sort_metric AUTO (check sorting by mean_per_class_error)
  aml3 <- h2o.automl(y = 5, training_frame = fr3, max_models = 2,
                     project_name = "r_lbsm_test_aml3_auto")
  aml3@leaderboard
  # check that leaderboard sorted by mean_per_class_error
  mpce_col <- as.vector(aml3@leaderboard[,"mean_per_class_error"])
  expect_equal(identical(mpce_col, sort(mpce_col, decreasing = FALSE)), TRUE)
 
}

doTest("AutoML Sort Metric Test", automl.leaderboard_sort_metric.test)
