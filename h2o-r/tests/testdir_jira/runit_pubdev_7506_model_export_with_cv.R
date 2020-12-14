setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.model_export_with_cv <- function() {

  Log.info("Importing prostate.csv...")
  prostate.hex <- h2o.importFile(normalizePath(locate('smalldata/logreg/prostate.csv')))
  prostate.hex[,2] <- as.factor(prostate.hex[,2])

  Log.info("Build GBM model")
  prostate.gbm <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 2,
                          keep_cross_validation_predictions = TRUE)

  holdout.preds <- as.data.frame(h2o.getFrame(prostate.gbm@model$cross_validation_holdout_predictions_frame_id$name))

  gbm_dir_download <- sandboxMakeSubDir(dirname = "gbm_export_with_cv")
  exported.gbm.path <- h2o.saveModel(prostate.gbm, path = gbm_dir_download, export_cv_predictions = TRUE)
  Log.info(paste("Model saved in", exported.gbm.path))

  # All keys removed to test that cross validation models are actually being loaded
  h2o.removeAll()

  reloaded_prostate.gbm <- h2o.loadModel(exported.gbm.path)
  reloaded_holdout.preds <- as.data.frame(h2o.getFrame(reloaded_prostate.gbm@model$cross_validation_holdout_predictions_frame_id$name))

  expect_equal(holdout.preds, reloaded_holdout.preds)
}

doTest("PUBDEV-7506 Test: Export models with CV holdout predictions", test.model_export_with_cv)
