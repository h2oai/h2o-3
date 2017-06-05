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
  
  print("Training, validaion & leaderboard frame")
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
  expect_equal(nrow_aml8_lb, 2)
  
  print("Check max_models > 1; leaderboard continuity/growth")
  aml8 <- h2o.automl(x = x, y = y, 
                     training_frame = train,
                     max_runtime_secs = max_runtime_secs,
                     max_models = 3,
                     project_name = "aml8")
  expect_equal(nrow(aml8@leaderboard) > nrow_aml8_lb,TRUE)
  
  
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
  amodel <- h2o.getModel(tail(aml9@leaderboard, 1)$model_id)
  if(grepl("^StackedEnsemble",amodel@model_id)){
    print("Last model in leaderboard is Stacked Ensemble. Will need to use second to last for fold_column test")
    amodel <- h2o.getModel(head(tail(aml9@leaderboard, 2), 1)$model_id)
    amodel_fold_column <- amodel@parameters$fold_column$column_name
    expect_equal(amodel_fold_column, fold_column)
  }else{
    amodel_fold_column <- amodel@parameters$fold_column$column_name
    expect_equal(amodel_fold_column, fold_column)
  }
  
  print("Check weights_column")
  aml10 <- h2o.automl(x = x, y = y, 
                      training_frame = train,
                      weights_column = weights_column,
                      max_runtime_secs = max_runtime_secs,
                      project_name = "aml10")
  amodel <- h2o.getModel(tail(aml10@leaderboard, 1)$model_id)
  if(grepl("^StackedEnsemble",amodel@model_id)){
      print("Last model in leaderboard is Stacked Ensemble. Will need to use second to last for weights_column test")
      amodel <- h2o.getModel(head(tail(aml10@leaderboard, 2), 1)$model_id)
      amodel_weights_column <- amodel@parameters$weights_column$column_name
      expect_equal(amodel_weights_column, weights_column)
  }else{
      amodel_weights_column <- amodel@parameters$weights_column$column_name
      expect_equal(amodel_weights_column, weights_column)
  }
  
  print("Check fold_colum and weights_column")
  aml11 <- h2o.automl(x = x, y = y, 
                      training_frame = train,
                      fold_column = fold_column,
                      weights_column = weights_column,
                      max_runtime_secs = max_runtime_secs,
                      project_name = "aml11")
  amodel <- h2o.getModel(tail(aml11@leaderboard, 1)$model_id)
  if(grepl("^StackedEnsemble",amodel@model_id)){
      print("Last model in leaderboard is Stacked Ensemble. Will need to use second to last for fold/weight column test")
      amodel <- h2o.getModel(head(tail(aml11@leaderboard, 2), 1)$model_id)
      amodel_fold_column <- amodel@parameters$fold_column$column_name
      expect_equal(amodel_fold_column, fold_column)
      amodel_weights_column <- amodel@parameters$weights_column$column_name
      expect_equal(amodel_weights_column, weights_column)
  }else{
      amodel_fold_column <- amodel@parameters$fold_column$column_name
      expect_equal(amodel_fold_column, fold_column)
      amodel_weights_column <- amodel@parameters$weights_column$column_name
      expect_equal(amodel_weights_column, weights_column)
  }
  
}

doTest("AutoML Args Test", automl.args.test)

