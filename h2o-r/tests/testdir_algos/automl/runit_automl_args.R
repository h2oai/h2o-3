setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.args.test <- function() {

  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.automl executes w/o errors
  # 2) That the arguments are working properly

  run_each_test_in_isolation <- TRUE      # can be disabled to minimize slowdown when running test suite in client mode on Jenkins
  max_models <- 2

  import_dataset <- function(cleanup = run_each_test_in_isolation) {
    if (cleanup) h2o.removeAll()

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

  get_partitioned_models <- function(aml) {
    model_ids <- as.character(as.data.frame(aml@leaderboard[,"model_id"])[,1])
    ensemble_model_ids <- grep("StackedEnsemble", model_ids, value = TRUE, invert = FALSE)
    non_ensemble_model_ids <- model_ids[!(model_ids %in% ensemble_model_ids)]
    return(list(all=model_ids, se=ensemble_model_ids, non_se=non_ensemble_model_ids))
  }


  print("Check arguments to H2OAutoML class")

  test_without_y <- function() {
    print("Try without a y")
    ds <- import_dataset()
    expect_error(h2o.automl(training_frame = ds$train,
                              max_models = max_models,
                              project_name = "aml0"))
  }

  test_without_x <- function() {
    print("Try without an x")
    ds <- import_dataset()
    h2o.automl(y = ds$y,
              training_frame = ds$train,
              max_models = max_models,
              project_name = "aml1")
  }

  test_y_as_index_x_as_name <- function() {
    print("Try with y as a column index, x as colnames")
    ds <- import_dataset()
    h2o.automl(x = ds$x, y = ds$y.idx,
              training_frame = ds$train,
              max_models = max_models,
              project_name = "aml2",
    )
  }

  test_single_training_frame <- function() {
    print("Single training frame; x and y both specified")
    ds <- import_dataset()
    h2o.automl(x = ds$x, y = ds$y,
              training_frame = ds$train,
              max_models = max_models,
              project_name = "aml3",
    )
  }

  test_training_with_validation_frame <- function() {
    print("Training & validation frame")
    ds <- import_dataset()
    h2o.automl(x = ds$x, y = ds$y,
              training_frame = ds$train,
              validation_frame = ds$valid,
              max_models = max_models,
              project_name = "aml4",
    )
  }

  test_training_with_leaderboard_frame <- function() {
    print("Training & leaderboard frame")
    ds <- import_dataset()
    h2o.automl(x = ds$x, y = ds$y,
              training_frame = ds$train,
              leaderboard_frame = ds$test,
              max_models = max_models,
              project_name = "aml5",
    )
  }

  test_training_with_validation_and_leaderboard_frame <- function() {
    print("Training, validation & leaderboard frame")
    ds <- import_dataset()
    h2o.automl(x = ds$x, y = ds$y,
              training_frame = ds$train,
              validation_frame = ds$valid,
              leaderboard_frame = ds$test,
              max_models = max_models,
              project_name = "aml6",
    )
  }

  test_early_stopping <- function() {
    print("Early stopping args")
    ds <- import_dataset()
    h2o.automl(x = ds$x, y = ds$y,
              training_frame = ds$train,
              validation_frame = ds$valid,
              leaderboard_frame = ds$test,
              max_models = max_models,
              stopping_metric = "AUC",
              stopping_tolerance = 0.001,
              stopping_rounds = 3,
              project_name = "aml7",
    )
  }

  test_leaderboard_growth <- function() {
    print("Check max_models = 1")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    max_models = 1,
                    project_name = "aml8",
    )
    nrow_lb <- nrow(aml@leaderboard)
    expect_equal(nrow_lb, 1)

    print("Check max_models > 1; leaderboard continuity/growth")
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    max_models = max_models,
                    project_name = "aml8",
    )
    expect_equal(nrow(aml@leaderboard) > nrow_lb, TRUE)
  }

  test_fold_column <- function() {
    ds <- import_dataset()
    fold_column <- "fold_id"
    ds$train[,fold_column] <- as.h2o(data.frame(rep(seq(1:3), 2000)[1:nrow(ds$train)]))

    print("Check fold_column")
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    fold_column = fold_column,
                    max_models = max_models,
                    keep_cross_validation_models = TRUE,
                    project_name = "aml9",
    )
    models <- get_partitioned_models(aml)
    amodel <- h2o.getModel(grep("DRF", models$non_se, value = TRUE))
    amodel_fold_column <- amodel@parameters$fold_column$column_name
    expect_equal(amodel_fold_column, fold_column)
    ensemble <- h2o.getModel(models$se[1])
    ensemble_fold_column <- ensemble@parameters$metalearner_fold_column$column_name
    expect_equal(ensemble_fold_column, fold_column)
    ensemble_meta <- h2o.getModel(ensemble@model$metalearner$name)
    expect_equal(length(ensemble_meta@model$cross_validation_models), 3)
  }


  test_weights_column <- function() {
    print("Check weights_column")
    ds <- import_dataset()
    weights_column <- "weight"
    ds$train[,weights_column] <- as.h2o(data.frame(runif(n = nrow(ds$train), min = 0, max = 5)))

    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    weights_column = weights_column,
                    max_models = max_models,
                    project_name = "aml10"
    )
    amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml)$non_se, value = TRUE))
    amodel_weights_column <- amodel@parameters$weights_column$column_name
    expect_equal(amodel_weights_column, weights_column)
  }

  test_fold_column_with_weights_column <- function() {
    print("Check fold_colum and weights_column")
    ds <- import_dataset()
    fold_column <- "fold_id"
    ds$train[,fold_column] <- as.h2o(data.frame(rep(seq(1:3), 2000)[1:nrow(ds$train)]))
    weights_column <- "weight"
    ds$train[,weights_column] <- as.h2o(data.frame(runif(n = nrow(ds$train), min = 0, max = 5)))

    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    fold_column = fold_column,
                    weights_column = weights_column,
                    max_models = max_models,
                    project_name = "aml11",
    )
    amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml)$non_se, value = TRUE))
    amodel_fold_column <- amodel@parameters$fold_column$column_name
    expect_equal(amodel_fold_column, fold_column)
    amodel_weights_column <- amodel@parameters$weights_column$column_name
    expect_equal(amodel_weights_column, weights_column)
  }

  test_nfolds_set_to_base_model <- function() {
    print("Check that nfolds is piped through properly to base models (nfolds > 1)")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = 3,
                    max_models = max_models,
                    project_name = "aml12"
    )
    amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml)$non_se, value = TRUE))
    expect_equal(amodel@parameters$nfolds, 3)
  }

  test_nfolds_eq_0 <- function() {
    print("Check that nfolds = 0 works properly")  #will need to change after xval leaderboard is implemented
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = 0,
                    max_models = max_models,
                    project_name = "aml13",
    )
    # Check that leaderboard does not contain any SEs
    amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml)$non_se, value = TRUE))
    expect_equal(amodel@allparameters$nfolds, 0)
  }

  test_stacked_ensembles_trained <- function() {
    print("Check that two Stacked Ensembles are trained")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = 3,
                    max_models = max_models,
                    project_name = "aml14",
    )
    # Check that leaderboard contains exactly two SEs: all model ensemble & top model ensemble
    expect_equal(length(get_partitioned_models(aml)$se), 2)
  }

  test_balance_classes <- function() {
    print("Check that balance_classes is working")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = 3,
                    max_models = max_models,
                    balance_classes = TRUE,
                    max_after_balance_size = 3.0,
                    class_sampling_factors = c(0.2, 1.4),
                    project_name = "aml15",
    )
    # Check that a model (DRF) has balance_classes args set properly
    amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml)$non_se, value = TRUE))
    expect_equal(amodel@parameters$balance_classes, TRUE)
    expect_equal(amodel@parameters$max_after_balance_size, 3.0)
    expect_equal(amodel@parameters$class_sampling_factors, c(0.2, 1.4))
  }

  test_keep_cv_models_and_keep_cv_predictions <- function() {
    print("Check that cv preds/models are deleted")
    ds <- import_dataset()
    nfolds <- 3
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = nfolds,
                    max_models = max_models,
                    project_name = "aml15",
                    keep_cross_validation_models = FALSE,
                    keep_cross_validation_predictions = FALSE,
    )

    model_ids <- as.character(as.data.frame(aml@leaderboard[,"model_id"])[,1])
    model_ids <- setdiff(model_ids, grep("StackedEnsemble", model_ids, value = TRUE))
    cv_model_ids <- list(NULL)
    for(i in 1:nfolds) {
      cv_model_ids[[i]] <- paste0(model_ids, "_cv_", i)
    }
    cv_model_ids <- unlist(cv_model_ids)
    expect_equal(sum(sapply(cv_model_ids, function(i) grepl(i, h2o.ls()))), 0)
  }

  test_keep_cv_fold_assignment_defaults_with_nfolds_gt_0 <- function() {
    print("Check that fold assignments were skipped by default and nfolds > 1")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = 3,
                    max_models = max_models,
                    project_name = "aml16",
    )
    some_base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
    expect_equal(some_base_model@parameters$keep_cross_validation_fold_assignment, NULL)
    expect_equal(some_base_model@model$cross_validation_fold_assignment_frame_id, NULL)
  }

  test_keep_cv_fold_assignment_TRUE_with_nfolds_gt_0 <- function() {
    print("Check that fold assignments were kept when `keep_cross_validation_fold_assignment`= TRUE and nfolds > 1")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                    training_frame = ds$train,
                    nfolds = 3,
                    max_models = max_models,
                    project_name = "aml17",
                    keep_cross_validation_fold_assignment = TRUE,
    )
    base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
    expect_equal(base_model@parameters$keep_cross_validation_fold_assignment, TRUE)
    expect_equal(length(base_model@model$cross_validation_fold_assignment_frame_id), 4)
  }

  test_keep_cv_fold_assignment_TRUE_with_nfolds_eq_0 <- function() {
    print("Check that fold assignments were skipped when `keep_cross_validation_fold_assignment`= TRUE and nfolds = 0")
    ds <- import_dataset()
    aml <- h2o.automl(x = ds$x, y = ds$y,
                        training_frame = ds$train,
                        nfolds = 0,
                        max_models = max_models,
                        project_name = "aml18",
                        keep_cross_validation_fold_assignment = TRUE,
    )
    base_model <- h2o.getModel(get_partitioned_models(aml)$non_se[1])
    expect_equal(base_model@parameters$keep_cross_validation_fold_assignment, NULL)
    expect_equal(base_model@model$cross_validation_fold_assignment_frame_id, NULL)
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

  test_max_models <- function() {
    print("Check that automl get interrupted after `max_models`")
    ds <- import_dataset()
    aml <- h2o.automl(x=ds$x, y=ds$y,
                      training_frame=ds$train,
                      project_name="aml_max_models",
                      max_models=max_models,
    )
    models <- get_partitioned_models(aml)
    expect_equal(length(models$non_se), max_models)
    expect_equal(length(models$se), 2)
  }


  tests <- c(
    test_without_y,
    test_without_x,
    test_y_as_index_x_as_name,
    test_single_training_frame,
    test_training_with_validation_frame,
    test_training_with_leaderboard_frame,
    test_training_with_validation_and_leaderboard_frame,
    test_early_stopping,
    test_leaderboard_growth,
    test_fold_column,
    test_weights_column,
    test_fold_column_with_weights_column,
    test_nfolds_set_to_base_model,
    test_nfolds_eq_0,
    test_stacked_ensembles_trained,
    test_balance_classes,
    test_keep_cv_models_and_keep_cv_predictions,
    test_keep_cv_fold_assignment_defaults_with_nfolds_gt_0,
    test_keep_cv_fold_assignment_TRUE_with_nfolds_gt_0,
    test_keep_cv_fold_assignment_TRUE_with_nfolds_eq_0,
    test_max_runtime_secs,
    test_max_models
  )

  lapply(tests, function(test) print(system.time(test()))) #need to monitor why this test suite is taking so long in client mode
}

doTest("AutoML Args Test", automl.args.test)

