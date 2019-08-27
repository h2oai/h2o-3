setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.timing.test <- function() {

    # This test checks the following (for binomial classification):
    #
    # 1) That h2o.automl executes w/o errors
    # 2) That the arguments are working properly

    max_models <- 2

    import_dataset <- function() {
        y <- "CAPSULE"
        y.idx <- 1

        keys <- h2o.ls()$key
        if ("rtest_automl_args_train" %in% keys) {
            print("using existing dataset")
            train <- h2o.getFrame("rtest_automl_args_train")
            valid <- h2o.getFrame("rtest_automl_args_valid")
            test <- h2o.getFrame("rtest_automl_args_test")
        } else {
            print("uploading dataset")
            train <- h2o.importFile(locate("smalldata/testng/prostate_train.csv"), destination_frame = "prostate_full_train")
            train[,y] <- as.factor(train[,y])
            train <- as.h2o(train, "rtest_automl_args_train")
            test <- h2o.importFile(locate("smalldata/testng/prostate_test.csv"), destination_frame = "prostate_full_test")
            test[,y] <- as.factor(test[,y])
            ss <- h2o.splitFrame(test, destination_frames=c("rtest_automl_args_valid", "rtest_automl_args_test"), seed = 1)
            valid <- ss[[1]]
            test <- ss[[2]]
        }

        x <- setdiff(names(train), y)
        return(list(x=x, y=y, y.idx=y.idx, train=train, valid=valid, test=test))
    }

    test_max_runtime_secs <- function() {
        print("Check that automl gets interrupted after `max_runtime_secs`")
        ds <- import_dataset()
        max_runtime_secs <- 60
        cancel_tolerance_secs <- 6+5 # should be enough for most cases given job notification mechanism (adding 5 more ~=10% for SEs)
        time <- system.time(aml <- h2o.automl(x=ds$x, y=ds$y,
                            training_frame=ds$train,
                            project_name="aml_max_runtime_secs",
                            max_runtime_secs=max_runtime_secs)
        )[['elapsed']]
        print(paste("trained", length(get_partitioned_models(aml)$non_se), "models"))
        expect_lte(abs(time - max_runtime_secs), cancel_tolerance_secs)
        expect_equal(length(get_partitioned_models(aml)$se), 2)
    }

    test_max_runtime_secs_per_model <- function() {
      print("Check that individual model get interrupted after `max_runtime_secs_per_model`")
      # need a larger dataset to obtain significant differences in behaviour
      train <- h2o.importFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="prostate_complete")
      y <- 'CAPSULE'
      x <- setdiff(names(train), y)
      ds <- list(train=train, x=x, y=y)

      max_runtime_secs <- 30
      models_count <- list()
      for (max_runtime_secs_per_model in c(0, 3, max_runtime_secs)) {
        aml <- h2o.automl(x=ds$x, y=ds$y,
                          training_frame=ds$train,
                          seed=1,
                          project_name=paste0("aml_max_runtime_secs_per_model_", max_runtime_secs_per_model),
                          max_runtime_secs_per_model=max_runtime_secs_per_model,
                          max_runtime_secs=max_runtime_secs)
        models_count[paste0(max_runtime_secs_per_model)] = nrow(aml@leaderboard)
      }
      expect_lte(abs(models_count[[paste0(0)]] - models_count[[paste0(max_runtime_secs)]]), 1)
      expect_gt(abs(models_count[[paste0(0)]] - models_count[[paste0(3)]]), 1)
      # TODO: add assertions about single model timing once 'automl event_log' is available on client side
    }

    makeSuite(
        test_max_runtime_secs,
        test_max_runtime_secs_per_model,
    )
}

doSuite("AutoML Timing Test", automl.timing.test(), time_monitor=TRUE)
