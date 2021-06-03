setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.cm.xval <- function() {
  pros.hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])

  pros.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.hex, nfold = 3, ntree = 5,
                      keep_cross_validation_predictions=TRUE)
  holdout_preds <- h2o.cross_validation_holdout_predictions(pros.gbm)

  cm_xval <- h2o.confusionMatrix(pros.gbm, xval = TRUE)
  print(cm_xval)

  metrics_recalculated <- h2o.make_metrics(holdout_preds$p1, pros.hex$CAPSULE, domain = c("0", "1"))
  cm_expected <- h2o.confusionMatrix(metrics_recalculated)
  print(cm_expected)

  expect_equal(as.matrix(cm_expected), as.matrix(cm_xval))
}

doTest("Testing h2o.confusionMatrix on a model with validation frame", test.cm.xval)
