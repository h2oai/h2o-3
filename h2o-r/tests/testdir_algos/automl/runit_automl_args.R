setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.args.test <- function() {

  get_partitioned_models <- function(aml) {
    model_ids <- as.character(as.data.frame(aml@leaderboard[,"model_id"])[,1])
    ensemble_model_ids <- grep("StackedEnsemble", model_ids, value = TRUE, invert = FALSE)
    non_ensemble_model_ids <- model_ids[!(model_ids %in% ensemble_model_ids)]
    list(all=model_ids, se=ensemble_model_ids, non_se=non_ensemble_model_ids)
  }

  # This test checks the following (for binomial classification):
  #
  # 1) That h2o.automl executes w/o errors
  # 2) That the arguments are working properly

  # Load data and split into train, valid and test sets
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
  max_models <- 2
  print("Check arguments to H2OAutoML class")

  #print("Try without a y")
  #expect_failure(h2o.automl(training_rframe = train,
  #                          max_models = max_models,
  #                          project_name = "aml0"))

  print("Try without an x")
  aml1 <- h2o.automl(y = y,
                     training_frame = train,
                     max_models = max_models,
                     project_name = "aml1")

  print("Try with y as a column index, x as colnames")
  aml2 <- h2o.automl(x = x, y = 1,
                     training_frame = train,
                     max_models = max_models,
                     project_name = "aml2")

  print("Single training frame; x and y both specified")
  aml3 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     max_models = max_models,
                     project_name = "aml3")

  print("Training & validation frame")
  aml4 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     validation_frame = valid,
                     max_models = max_models,
                     project_name = "aml4")

  print("Training & leaderboard frame")
  aml5 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     leaderboard_frame = test,
                     max_models = max_models,
                     project_name = "aml5")

  print("Training, validation & leaderboard frame")
  aml6 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     validation_frame = valid,
                     leaderboard_frame = test,
                     max_models = max_models,
                     project_name = "aml6")

  print("Early stopping args")
  aml7 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     validation_frame = valid,
                     leaderboard_frame = test,
                     max_models = max_models,
                     stopping_metric = "AUC",
                     stopping_tolerance = 0.001,
                     stopping_rounds = 3,
                     project_name = "aml7")

  print("Check max_models = 1")
  aml8 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     max_models = 1,
                     project_name = "aml8")
  nrow_aml8_lb <- nrow(aml8@leaderboard)
  expect_equal(nrow_aml8_lb, 1)

  print("Check max_models > 1; leaderboard continuity/growth")
  aml8 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     max_models = max_models,
                     project_name = "aml8")
  expect_equal(nrow(aml8@leaderboard) > nrow_aml8_lb, TRUE)


  # Add a fold_column and weights_column
  fold_column <- "fold_id"
  weights_column <- "weight"
  train[,fold_column] <- as.h2o(data.frame(rep(seq(1:3), 2000)[1:nrow(train)]))
  train[,weights_column] <- as.h2o(data.frame(runif(n = nrow(train), min = 0, max = 5)))

  print("Check fold_column")
  aml9 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     fold_column = fold_column,
                     max_models = max_models,
                     keep_cross_validation_models = TRUE,
                     project_name = "aml9")
  models <- get_partitioned_models(aml9)
  amodel <- h2o.getModel(grep("DRF", models$non_se, value = TRUE))
  amodel_fold_column <- amodel@parameters$fold_column$column_name
  expect_equal(amodel_fold_column, fold_column)
  ensemble <- h2o.getModel(models$se[1])
  ensemble_fold_column <- ensemble@parameters$metalearner_fold_column$column_name
  expect_equal(ensemble_fold_column, fold_column)
  ensemble_meta <- h2o.getModel(ensemble@model$metalearner$name)
  expect_equal(length(ensemble_meta@model$cross_validation_models), 3)

  print("Check weights_column")
  aml10 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      weights_column = weights_column,
                      max_models = max_models,
                      project_name = "aml10")
  amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml10)$non_se, value = TRUE))
  amodel_weights_column <- amodel@parameters$weights_column$column_name
  expect_equal(amodel_weights_column, weights_column)

  print("Check fold_colum and weights_column")
  aml11 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      fold_column = fold_column,
                      weights_column = weights_column,
                      max_models = max_models,
                      project_name = "aml11")
  amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml11)$non_se, value = TRUE))
  amodel_fold_column <- amodel@parameters$fold_column$column_name
  expect_equal(amodel_fold_column, fold_column)
  amodel_weights_column <- amodel@parameters$weights_column$column_name
  expect_equal(amodel_weights_column, weights_column)

  print("Check that nfolds is piped through properly to base models (nfolds > 1)")
  aml12 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = max_models,
                      project_name = "aml12")
  amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml12)$non_se, value = TRUE))
  expect_equal(amodel@parameters$nfolds, 3)

  print("Check that nfolds = 0 works properly")  #will need to change after xval leaderboard is implemented
  aml13 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 0,
                      max_models = max_models,
                      project_name = "aml13")
  # Check that leaderboard does not contain any SEs
  amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml13)$non_se, value = TRUE))
  expect_equal(amodel@allparameters$nfolds, 0)

  print("Check that two Stacked Ensembles are trained")
  aml14 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = max_models,
                      project_name = "aml14")
  # Check that leaderboard contains exactly two SEs: all model ensemble & top model ensemble
  expect_equal(length(get_partitioned_models(aml14)$se), 2)
  
  print("Check that balance_classes is working")
  aml15 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = max_models,
                      balance_classes = TRUE,
                      max_after_balance_size = 3.0,  
                      class_sampling_factors = c(0.2, 1.4),
                      project_name = "aml15")
  # Check that a model (DRF) has balance_classes args set properly
  amodel <- h2o.getModel(grep("DRF", get_partitioned_models(aml15)$non_se, value = TRUE))
  expect_equal(amodel@parameters$balance_classes, TRUE)
  expect_equal(amodel@parameters$max_after_balance_size, 3.0)
  expect_equal(amodel@parameters$class_sampling_factors, c(0.2, 1.4))
  

  print("Check that cv preds/models are deleted")
  nfolds <- 3
  aml15 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = nfolds,
                      max_models = max_models,
                      project_name = "aml15",
                      keep_cross_validation_models = FALSE,
                      keep_cross_validation_predictions = FALSE)
                      model_ids <- as.character(as.data.frame(aml15@leaderboard[,"model_id"])[,1])
                      model_ids <- setdiff(model_ids, grep("StackedEnsemble", model_ids, value = TRUE))
  cv_model_ids <- list(NULL)
  for(i in 1:nfolds) {
      cv_model_ids[[i]] <- paste0(model_ids, "_cv_", i)
  }
  cv_model_ids <- unlist(cv_model_ids)
  expect_equal(sum(sapply(cv_model_ids, function(i) grepl(i, h2o.ls()))), 0)

  print("Check that fold assignments were skipped by default and nfolds > 1")
  aml16 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = max_models,
                      project_name = "aml16")
  some_base_model <- h2o.getModel(get_partitioned_models(aml16)$non_se[1])
  expect_equal(some_base_model@parameters$keep_cross_validation_fold_assignment, NULL)
  expect_equal(some_base_model@model$cross_validation_fold_assignment_frame_id, NULL)

  print("Check that fold assignments were kept when `keep_cross_validation_fold_assignment`= TRUE and nfolds > 1")
  aml17 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = max_models,
                      project_name = "aml17",
                      keep_cross_validation_fold_assignment = TRUE)
  base_model <- h2o.getModel(get_partitioned_models(aml17)$non_se[1])
  expect_equal(base_model@parameters$keep_cross_validation_fold_assignment, TRUE)
  expect_equal(length(base_model@model$cross_validation_fold_assignment_frame_id), 4)

  print("Check that fold assignments were skipped when `keep_cross_validation_fold_assignment`= TRUE and nfolds = 0")
  aml18 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 0,
                      max_models = max_models,
                      project_name = "aml18",
                      keep_cross_validation_fold_assignment = TRUE)
  base_model <- h2o.getModel(get_partitioned_models(aml18)$non_se[1])
  expect_equal(base_model@parameters$keep_cross_validation_fold_assignment, NULL)
  expect_equal(base_model@model$cross_validation_fold_assignment_frame_id, NULL)


  print("Check that automl gets interrupted after `max_runtime_secs`")
  max_runtime_secs <- 30
  cancel_tolerance_secs <- 5+3 # should be enough for most cases given job notification mechanism (adding 3=10% for SEs)
  time <- system.time(aml19 <- h2o.automl(x=x, y=y, training_frame=train,
                                         project_name="aml_max_runtime_secs",
                                         max_runtime_secs=max_runtime_secs))[['elapsed']]
  expect_lte(abs(time - max_runtime_secs), cancel_tolerance_secs)
  expect_equal(length(get_partitioned_models(aml19)$se), 2)


  print("Check that automl get interrupted after `max_models`")
  aml20 <- h2o.automl(x=x, y=y, training_frame=train,
                      project_name="aml_max_models",
                      max_models=max_models)
  models <- get_partitioned_models(aml20)
  expect_equal(length(models$non_se), max_models)
  expect_equal(length(models$se), 2)

}

doTest("AutoML Args Test", automl.args.test)

