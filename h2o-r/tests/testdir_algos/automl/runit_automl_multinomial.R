setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

automl.multinomial.test <- function() {
  
  # This test checks the following (for multinomial classification):
  #
  # 1) That h2o.automl executes w/o errors on multinomial data
  # 2) That the leaderboard contains a StackedEnsemble model

  # Example multiclass dataset
  train <- as.h2o(iris)

  # Run a multinomial AutoML
  aml <- h2o.automl(x = 1:4,
                    y = 5,
                    training_frame = train,
                    project_name = "automl.multinomial.test",
                    seed = 1, 
                    max_models = 3)
  
  # Check that there's a StackedEnsemble model in the leaderboard
  expect_true(sum(grepl("StackedEnsemble", as.vector(aml@leaderboard$model_id))) > 0)

  # Check auc_type setting  
  aml2 <- h2o.automl(x = 1:4, 
                    y = 5,
                    training_frame = train,
                    project_name = "automl.multinomial.test.auc",
                    seed = 1,
                    max_models = 3,
                    exclude_algos = list("StackedEnsemble"),
                    auc_type = "MACRO_OVO")
  model <- aml2@leader
  tr_mm <-  model@model$training_metrics
  print(tr_mm)  
  tr_auc <- tr_mm@metrics$AUC 
  perf <- h2o.performance(model = model, newdata = train, auc_type = "MACRO_OVO")
  perf_auc <- h2o.auc(perf)
  print(paste(tr_auc, "=",  perf_auc))
  expect_equal(tr_auc, perf_auc)
}

doTest("AutoML Multinomial Test", automl.multinomial.test)
