setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.score_eval_metric_only <- function() {
  expect_true(h2o.xgboost.available())
  
  n.trees <- 10
  max_depth <- 5
  min_rows <- 10
  learn_rate <- 0.7
  distribution <- "bernoulli"
  eval_metric <- "error@0.75"
  
  ecology.hex <- h2o.importFile(locate("smalldata/gbm_test/ecology_model.csv"))
  ecology.hex$Angaus <- as.factor(ecology.hex$Angaus)
  
  model_full <- h2o.xgboost(model_id = "xgb_ecology",
                            x = 3:13, 
                            y = "Angaus", 
                            training_frame = ecology.hex,
                            ntrees = n.trees,
                            max_depth = max_depth,
                            min_rows = min_rows,
                            learn_rate = learn_rate,
                            distribution = distribution,
                            eval_metric = eval_metric,
                            score_each_iteration = TRUE)
  output_full <- capture.output(print(model_full))
  history_full <- h2o.scoreHistory(model_full)
  
  h2o.rm(model_full)
  
  model_light <- h2o.xgboost(model_id = "xgb_ecology",
                             x = 3:13, 
                             y = "Angaus", 
                             training_frame = ecology.hex,
                             ntrees = n.trees,
                             max_depth = max_depth,
                             min_rows = min_rows,
                             learn_rate = learn_rate,
                             distribution = distribution,
                             eval_metric = eval_metric,
                             score_each_iteration = TRUE,
                             score_eval_metric_only = TRUE) # disable full scoring
  
  output_light <- capture.output(print(model_light))
  history_light <- h2o.scoreHistory(model_light)
  
  expect_equal(nrow(history_full), n.trees + 1)
  
  # User sees the same print output regardless of "light" scoring
  expect_equal(output_light, output_full)
  
  # Sanity check - we should have the custom/eval metric in the history
  expect_true("training_custom" %in% colnames(history_full))
  # It should be defined
  expect_false(any(is.na(history_full$training_custom)))
  # And in both cases it should be identical
  expect_equal(history_light$training_custom, history_full$training_custom)
  
  # Remove time-dependent columns
  history_full$timestamp <- NULL
  history_light$timestamp <- NULL
  history_full$duration <- NULL
  history_light$duration <- NULL
  
  # Final scoring iteration is always full - all metrics are recorded
  expect_equal(history_light[nrow(history_light),], history_full[nrow(history_full),])
  
  # Check that previous scoring history is empty for "light" scoring
  h2o_metric_cols <- grep("^training_", setdiff(colnames(history_light), "training_custom"))
  expect_true(all(is.na(as.matrix(history_light[1:nrow(history_light)-1, h2o_metric_cols]))))
}

doTest("XGBoost: Score only the evaluation metric", test.XGBoost.score_eval_metric_only)
