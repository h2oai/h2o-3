setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.eval_metric <- function() {
  expect_true(h2o.xgboost.available())
  
  n.trees <- 10
  max_depth <- 5
  min_rows <- 10
  learn_rate <- 0.7
  distribution <- "bernoulli"
  eval_metric <- "error@0.75"
  
  ecology.hex <- h2o.importFile(locate("smalldata/gbm_test/ecology_model.csv"))
  ecology.hex$Angaus <- as.factor(ecology.hex$Angaus)

  ecology.h2o <- h2o.xgboost(x = 3:13, 
                             y = "Angaus", 
                             training_frame = ecology.hex,
                             ntrees = n.trees,
                             max_depth = max_depth,
                             min_rows = min_rows,
                             learn_rate = learn_rate,
                             distribution = distribution,
                             eval_metric = eval_metric)
  print(ecology.h2o)
  predicted <- h2o.predict(ecology.h2o, ecology.hex)

  # Evaluation metric is in scoring history as "custom metric"
  expect_true("training_custom" %in% colnames(h2o.scoreHistory(ecology.h2o)))

  # Extract eval metric from scoring history and compare with calculated value
  act_error <- tail(h2o.scoreHistory(ecology.h2o)$training_custom, 1)
  exp_error <- sum((predicted$p1 >= 0.75) != ecology.hex$Angaus) / nrow(predicted)
  expect_equal(act_error, exp_error)
}

doTest("XGBoost: Evaluation Metric", test.XGBoost.eval_metric)
