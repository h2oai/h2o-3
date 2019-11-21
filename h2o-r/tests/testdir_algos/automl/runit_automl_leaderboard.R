setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.leaderboard.suite <- function() {

    # Test each ML task to make sure the leaderboard is working as expected:
    # Leaderboard columns are correct for each ML task
    # Check that excluded algos are not in the leaderboard
    # Since we are not running this a long time, we can't guarantee that DNN and GLM will be in there

    all_algos <- c("GLM", "DeepLearning", "GBM", "DRF", "XGBoost", "StackedEnsemble")

    test.binomial <- function() {
        # Binomial:
        fr <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
        fr["CAPSULE"] <- as.factor(fr["CAPSULE"])
        exclude_algos <- c("GLM", "DeepLearning", "DRF")  #Expect GBM + StackedEnsemble
        aml <- h2o.automl(y = 2, training_frame = fr, max_models = 10,
                           project_name = "r_aml_lb_binomial_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        # check that correct leaderboard columns exist
        expect_equal(names(aml@leaderboard), c("model_id", "auc", "logloss", "aucpr", "mean_per_class_error", "rmse", "mse"))
        model_ids <- as.vector(aml@leaderboard$model_id)
        # check that no excluded algos are present in leaderboard
        exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
        expect_equal(exclude_algo_count, 0)
        include_algos <- setdiff(all_algos, exclude_algos)
        # check that expected algos are included in leaderboard
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }


    test.regression <- function() {
        # Regression:
        fr <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"))
        exclude_algos <- c("GBM", "DeepLearning")  #Expect GLM, DRF (and XRT), XGBoost, StackedEnsemble
        aml <- h2o.automl(y = "runoffnew", training_frame = fr, max_models = 10,
                           project_name = "r_aml_lb_regression_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        expect_equal(names(aml@leaderboard), c("model_id", "deviance", "rmse", "mse", "mae", "rmsle"))
        model_ids <- as.vector(aml@leaderboard$model_id)
        # check that no excluded algos are present in leaderboard
        exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
        expect_equal(exclude_algo_count, 0)
        include_algos <- c(setdiff(all_algos, exclude_algos), "XRT")
        # check that expected algos are included in leaderboard
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }

    test.multinomial <- function() {
        # Multinomial:
        fr <- as.h2o(iris)
        exclude_algos <- c("XGBoost")
        aml <- h2o.automl(y = 5, training_frame = fr, max_models = 10,
                           project_name = "r_aml_lb_multinomial_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        expect_equal(names(aml@leaderboard),c("model_id", "mean_per_class_error", "logloss", "rmse", "mse"))
        model_ids <- as.vector(aml@leaderboard$model_id)
        # check that no excluded algos are present in leaderboard
        exclude_algo_count <- sum(sapply(exclude_algos, function(i) sum(grepl(i, model_ids))))
        expect_equal(exclude_algo_count, 0)
        # check that expected algos are included in leaderboard
        include_algos <- c(setdiff(all_algos, exclude_algos), "XRT")
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }

    test.empty_leaderboard <- function() {
        # Exclude all the algorithms, check for empty leaderboard
        fr <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))  #need to reload data (getting error otherwise)
        fr["CAPSULE"] <- as.factor(fr["CAPSULE"])
        exclude_algos <- c("GLM", "DRF", "GBM", "DeepLearning", "XGBoost", "StackedEnsemble")
        aml <- h2o.automl(y = 2, training_frame = fr, max_runtime_secs = 5,
                           project_name = "r_aml_lb_empty_test",
                           exclude_algos = exclude_algos)
        aml@leaderboard
        expect_equal(nrow(aml@leaderboard), 0)

        warnings = aml@event_log[aml@event_log['level'] == 'Warn','message']
        last_warning = warnings[nrow(warnings), 1]
        expect_true(grepl("Empty leaderboard", last_warning))
    }

    test.all_algos <- function() {
        # Include all algorithms (all should be there, given large enough max_models)
        fr <- as.h2o(iris)
        aml <- h2o.automl(y = 5, training_frame = fr, max_models = 12,
                           project_name = "r_aml_lb__all_algoes_test")
        model_ids <- as.vector(aml@leaderboard$model_id)
        include_algos <- c(all_algos, "XRT")
        for (a in include_algos) {
          expect_equal(sum(grepl(a, model_ids)) > 0, TRUE)
        }
    }

    makeSuite(
      test.binomial,
      test.regression,
      test.multinomial,
      test.empty_leaderboard,
      test.all_algos
    )
}

doSuite("AutoML Leaderboard Test", automl.leaderboard.suite())

