setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.args.test <- function() {

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
  max_runtime_secs <- 10

  print("Check arguments to H2OAutoML class")

  #print("Try without a y")
  #expect_failure(h2o.automl(training_frame = train,
  #                          max_runtime_secs = max_runtime_secs,
  #                          project_name = "aml0"))

  print("Try without an x")
  aml1 <- h2o.automl(y = y,
                     training_frame = train,
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml1")

  print("Try with y as a column index, x as colnames")
  aml2 <- h2o.automl(x = x, y = 1,
                     training_frame = train,
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml2")

  print("Single training frame; x and y both specified")
  aml3 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml3")

  print("Training & validation frame")
  aml4 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     validation_frame = valid,
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml4")

  print("Training & leaderboard frame")
  aml5 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     leaderboard_frame = test,
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml5")

  print("Training, validation & leaderboard frame")
  aml6 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     validation_frame = valid,
                     leaderboard_frame = test,
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml6")

  print("Early stopping args")
  aml7 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     validation_frame = valid,
                     leaderboard_frame = test,
                     max_runtime_secs = max_runtime_secs,
                     stopping_metric = "AUC",
                     stopping_tolerance = 0.001,
                     stopping_rounds = 3,
                     project_name = "aml7")

  print("Check max_models = 1")
  aml8 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     max_runtime_secs = max_runtime_secs,
                     max_models = 1,
                     project_name = "aml8")
  nrow_aml8_lb <- nrow(aml8@leaderboard)
  expect_equal(nrow_aml8_lb, 4)

  print("Check max_models > 1; leaderboard continuity/growth")
  aml8 <- h2o.automl(x = x, y = y,
                     training_frame = train,
                     max_runtime_secs = max_runtime_secs,
                     max_models = 3,
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
                     max_runtime_secs = max_runtime_secs,
                     project_name = "aml9")
  model_ids <- as.character(as.data.frame(aml9@leaderboard[,"model_id"])[,1])
  amodel <- h2o.getModel(grep("DRF", model_ids, value = TRUE))
  amodel_fold_column <- amodel@parameters$fold_column$column_name
  expect_equal(amodel_fold_column, fold_column)
  ensemble <- h2o.getModel(grep("StackedEnsemble", model_ids, value = TRUE)[1])
  ensemble_fold_column <- ensemble@parameters$metalearner_fold_column$column_name
  expect_equal(ensemble_fold_column, fold_column)
  ensemble_meta <- h2o.getModel(ensemble@model$metalearner$name)
  expect_equal(length(ensemble_meta@model$cross_validation_models), 3)

  print("Check weights_column")
  aml10 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      weights_column = weights_column,
                      max_runtime_secs = max_runtime_secs,
                      project_name = "aml10")
  model_ids <- as.character(as.data.frame(aml10@leaderboard[,"model_id"])[,1])
  amodel <- h2o.getModel(grep("DRF", model_ids, value = TRUE))
  amodel_weights_column <- amodel@parameters$weights_column$column_name
  expect_equal(amodel_weights_column, weights_column)

  print("Check fold_colum and weights_column")
  aml11 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      fold_column = fold_column,
                      weights_column = weights_column,
                      max_runtime_secs = max_runtime_secs,
                      project_name = "aml11")
  model_ids <- as.character(as.data.frame(aml11@leaderboard[,"model_id"])[,1])
  amodel <- h2o.getModel(grep("DRF", model_ids, value = TRUE))
  amodel_fold_column <- amodel@parameters$fold_column$column_name
  expect_equal(amodel_fold_column, fold_column)
  amodel_weights_column <- amodel@parameters$weights_column$column_name
  expect_equal(amodel_weights_column, weights_column)

  print("Check that nfolds is piped through properly to base models (nfolds > 1)")
  aml12 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = 3,
                      project_name = "aml12")
  model_ids <- as.character(as.data.frame(aml12@leaderboard[,"model_id"])[,1])
  amodel <- h2o.getModel(grep("DRF", model_ids, value = TRUE))
  expect_equal(amodel@parameters$nfolds, 3)

  print("Check that nfolds = 0 works properly")  #will need to change after xval leaderboard is implemented
  aml13 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 0,
                      max_models = 3,
                      project_name = "aml13")
  # Check that leaderboard does not contain any SEs
  model_ids <- as.character(as.data.frame(aml13@leaderboard[,"model_id"])[,1])
  amodel <- h2o.getModel(grep("DRF", model_ids, value = TRUE))
  expect_equal(amodel@allparameters$nfolds, 0)

  print("Check that two Stacked Ensembles are trained")
  aml14 <- h2o.automl(x = x, y = y,
                      training_frame = train,
                      nfolds = 3,
                      max_models = 6,
                      project_name = "aml14")
  # Check that leaderboard contains exactly two SEs: all model ensemble & top model ensemble
  model_ids <- as.character(as.data.frame(aml14@leaderboard[,"model_id"])[,1])
  expect_equal(sum(grepl("StackedEnsemble", model_ids)), 3)

}

doTest("AutoML Args Test", automl.args.test)

