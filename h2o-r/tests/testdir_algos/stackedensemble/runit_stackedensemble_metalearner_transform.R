setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.metalearner_transform_works <- function() {
  train <- h2o.uploadFile(locate("smalldata/extdata/prostate.csv"))
  y <- "CAPSULE"
  train[[y]] <- as.factor(train[[y]])
  nfolds <- 5

  my_rf <- h2o.randomForest(y = y,
                            training_frame = train,
                            ntrees = 10,
                            nfolds = nfolds,
                            fold_assignment = "Modulo",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)

  my_xrf <- h2o.randomForest(y = y,
                             training_frame = train,
                             ntrees = 10,
                             histogram_type = "Random",
                             nfolds = nfolds,
                             fold_assignment = "Modulo",
                             keep_cross_validation_predictions = TRUE,
                             seed = 1)

  stack_notransform <- h2o.stackedEnsemble(y = y,
                                           training_frame = train,
                                           base_models = list(my_rf, my_xrf),
                                           metalearner_transform = "none",
                                           seed = 1)

  stack_logit <- h2o.stackedEnsemble(y = y,
                                     training_frame = train,
                                     base_models = list(my_rf, my_xrf),
                                     metalearner_transform = "logit",
                                     seed = 1)

  stack_percentile_rank <- h2o.stackedEnsemble(y = y,
                                               training_frame = train,
                                               base_models = list(my_rf, my_xrf),
                                               metalearner_transform = "percentile_rank",
                                               seed = 1)

  stack_percentile_rank2 <- h2o.stackedEnsemble(y = y,
                                               training_frame = train,
                                               base_models = list(my_rf, my_xrf),
                                               metalearner_transform = "PercentileRank",
                                               seed = 1)

  # Check that the metalearner transform was set properly
  expect_equal(stack_logit@parameters$metalearner_transform, "Logit")
  expect_equal(stack_percentile_rank@parameters$metalearner_transform, "PercentileRank")
  expect_equal(stack_percentile_rank@parameters$metalearner_transform, stack_percentile_rank2@parameters$metalearner_transform)

  # Check we can predict
  preds <- predict(stack_notransform, train)
  preds_logit <- predict(stack_logit, train)
  preds_pr <- predict(stack_percentile_rank, train)

  # Sanity check
  expect_equal(nrow(preds_logit), nrow(preds))
  expect_equal(nrow(preds_pr), nrow(preds))

  # Check that at least some of the probs are different
  expect_true(as.logical(any(abs(preds$p0 - preds_logit$p0) > 1e-3)))
  expect_true(as.logical(any(abs(preds$p0 - preds_pr$p0) > 1e-3)))
}

doTest("Stacked Ensemble works with metalearner transform", stackedensemble.metalearner_transform_works)
