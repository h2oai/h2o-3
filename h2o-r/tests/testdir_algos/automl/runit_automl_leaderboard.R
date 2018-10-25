setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.leaderboard.test <- function() {
  
    # Test each ML task to make sure the leaderboard is working as expected:
    # Leaderboard columns are correct for each ML task 
    # Check that excluded algos are not in the leaderboard
    # Since we are not running this a long time, we can't guarantee that DNN and GLM will be in there
  
    all_algos <- c("GLM", "DeepLearning", "GBM", "DRF", "XGBoost", "StackedEnsemble")
  
    # Binomial:
    fr1 <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
    fr1["CAPSULE"] <- as.factor(fr1["CAPSULE"])
    exclude_algos <- c("GLM", "DeepLearning", "DRF")  #Expect GBM + StackedEnsemble
    aml1 <- h2o.automl(y = 2, training_frame = fr1, max_models = 10,
                       project_name = "r_lb_test_aml1",
                       exclude_algos = exclude_algos)
    aml1@leaderboard
    # check that correct leaderboard columns exist
    expect_equal(names(aml1@leaderboard), c("model_id", "auc", "logloss", "mean_per_class_error", "rmse", "mse"))
    model_ids <- as.vector(aml1@leaderboard$model_id)
    # check that no excluded algos are present in leaderboard
    exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
    expect_equal(exclude_algo_count, 0)
    include_algos <- setdiff(all_algos, exclude_algos)
    # check that expected algos are included in leaderboard
    for (a in include_algos) {
      expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
    }
    

    # Regression:
    fr2 <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"))
    exclude_algos <- c("GBM", "DeepLearning")  #Expect GLM, DRF (and XRT), XGBoost, StackedEnsemble
    aml2 <- h2o.automl(y = "runoffnew", training_frame = fr2, max_models = 10,
                       project_name = "r_lb_test_aml2",
                       exclude_algos = exclude_algos)
    aml2@leaderboard
    expect_equal(names(aml2@leaderboard), c("model_id", "mean_residual_deviance", "rmse", "mse", "mae", "rmsle"))
    model_ids <- as.vector(aml2@leaderboard$model_id)
    # check that no excluded algos are present in leaderboard
    exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
    expect_equal(exclude_algo_count, 0)
    include_algos <- c(setdiff(all_algos, exclude_algos), "XRT")
    # check that expected algos are included in leaderboard
    for (a in include_algos) {
      expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
    }

    # Multinomial:
    fr3 <- as.h2o(iris)
    exclude_algos <- c("XGBoost")
    aml3 <- h2o.automl(y = 5, training_frame = fr3, max_models = 10,
                       project_name = "r_lb_test_aml3",
                       exclude_algos = exclude_algos)
    aml3@leaderboard
    expect_equal(names(aml3@leaderboard),c("model_id", "mean_per_class_error", "logloss", "rmse", "mse"))
    model_ids <- as.vector(aml3@leaderboard$model_id)
    # check that no excluded algos are present in leaderboard
    exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
    expect_equal(exclude_algo_count, 0)
    # check that expected algos are included in leaderboard
    include_algos <- c(setdiff(all_algos, exclude_algos), "XRT")
    for (a in include_algos) {
      expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
    }

    
    # Exclude all the algorithms, check for empty leaderboard
    fr1 <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))  #need to reload data (getting error otherwise)
    fr1["CAPSULE"] <- as.factor(fr1["CAPSULE"])
    exclude_algos <- c("GLM", "DRF", "GBM", "DeepLearning", "XGBoost", "StackedEnsemble")
    aml4 <- h2o.automl(y = 2, training_frame = fr1, max_runtime_secs = 5,
                       project_name = "r_lb_test_aml4",
                       exclude_algos = exclude_algos)
    aml4@leaderboard
    # there's a dummy row of NAs in an "empty" leaderboard
    expect_equal(nrow(aml4@leaderboard), 1)
    
    # Include all algorithms (all should be there, given large enough max_models)
    fr3 <- as.h2o(iris)
    aml5 <- h2o.automl(y = 5, training_frame = fr3, max_models = 12,
                       project_name = "r_lb_test_aml5")
    model_ids <- as.vector(aml5@leaderboard$model_id)
    include_algos <- c(all_algos, "XRT")
    for (a in include_algos) {
      expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
    }
    
}

doTest("AutoML Leaderboard Test", automl.leaderboard.test)
