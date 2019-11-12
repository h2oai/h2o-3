setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.cleanup.suite <- function() {

  max_models <- 3

  import_dataset <- function() {
    y <- "CAPSULE"

    train <- h2o.importFile(locate("smalldata/testng/prostate_train.csv"), destination_frame = "aml_r_train_dataset")
    train[,y] <- as.factor(train[,y])
    train <- as.h2o(train, "aml_r_train_dataset")
    test <- h2o.importFile(locate("smalldata/testng/prostate_test.csv"), destination_frame = "aml_r_test_dataset")
    test[,y] <- as.factor(test[,y])
    ss <- h2o.splitFrame(test, destination_frames=c("aml_r_test_valid", "aml_r_test_dataset"), seed = 1)
    valid <- ss[[1]]
    test <- ss[[2]]

    x <- setdiff(names(train), y)
    return(list(x=x, y=y, train=train, valid=valid, test=test))
  }

  test_remove_automl_instance <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(project_name="test_remove_R_automl_instance",
                      x=ds$x, y=ds$y,
                      training_frame=ds$train,
                      validation_frame=ds$valid,
                      leaderboard_frame=ds$test,
                      max_models=max_models)

    keys <- h2o.ls()$key
    expect_gt(length(grep("_AutoML_", keys)), nrow(aml@leaderboard))

    h2o.rm(aml)
    clean <- h2o.ls()$key
    print(clean)
    expect_equal(length(grep("_AutoML_", !!clean)), 0)
    # verify that the original frames were not deleted
    for (frame in c(ds$train, ds$valid, ds$test)) {
      expect_true(any(grepl(paste0("^",h2o.getId(frame),"$"), clean)))
    }
  }

  test_remove_automl_instance_after_keeping_cv <- function() {
    ds <- import_dataset()
    aml <- h2o.automl(project_name="test_remove_R_automl_instance_after_keeping_cv",
                      x=ds$x, y=ds$y,
                      training_frame=ds$train,
                      validation_frame=ds$valid,
                      leaderboard_frame=ds$test,
                      max_models=max_models,
                      keep_cross_validation_models=TRUE,
                      keep_cross_validation_predictions=TRUE,
                      keep_cross_validation_fold_assignment=TRUE
                      )

    keys <- h2o.ls()$key
    expect_gt(length(grep("_AutoML_", keys)), nrow(aml@leaderboard))

    h2o.rm(aml)
    clean <- h2o.ls()$key
    print(clean)
    expect_equal(length(grep("_AutoML_", !!clean)), 0)
    # verify that the original frames were not deleted
    for (frame in c(ds$train, ds$valid, ds$test)) {
      expect_true(any(grepl(paste0("^",h2o.getId(frame),"$"), clean)))
    }
  }

  makeSuite(
    test_remove_automl_instance,
    test_remove_automl_instance_after_keeping_cv
  )
}

doSuite("AutoML Memory Cleanup Test", automl.cleanup.suite())



