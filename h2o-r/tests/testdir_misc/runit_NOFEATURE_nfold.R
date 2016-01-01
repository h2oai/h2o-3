setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.h2o.nfold <- function() {
  tolerance <- 1e-2

  hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/logreg/prostate.csv")))
  predictors = c(3:9)
  response = 2
  NFOLDS = 4

  # let GBM do the n-fold CV
  m <- h2o.gbm(x = predictors, y = response, training_frame = hex, nfolds=NFOLDS)

  # now do the same by hand
  # H2O doesn't support writes into a row range at this point, do in R instead for simplicity
  predictions = numeric(length = nrow(hex))
  offset = 1
  for (i in 1:NFOLDS) {
    folds = h2o.nFoldExtractor(hex, nfolds=NFOLDS, fold_to_extract = i)
    train = folds[[1]]
    valid = folds[[2]]
    model <- h2o.gbm(x = predictors, y = response, training_frame = train)
    pred <- h2o.predict(model, valid)
    len <- nrow(valid)
    pred.R <- as.data.frame(pred[,3])
    predictions[offset:(offset+len-1)] <- as.matrix(pred.R)
    offset <- offset + len
  }

  # compare metrics
  perf_auc <- h2o.performance(as.h2o(predictions), hex[,response], measure = "F1")
  perf_cm <- h2o.performance(as.h2o(predictions), hex[,response], thresholds = m@model$best_cutoff)
  auc <- m@model$auc
  accuracy <- m@model$accuracy
  cm <- m@model$confusion

  auc
  perf_auc@model$auc
  if (abs(auc - perf_auc@model$auc) > tolerance) stop("AUC is wrong")

  accuracy
  perf_cm@model$accuracy
  if (abs(accuracy - perf_cm@model$accuracy) > tolerance) stop("accuracy is wrong")

  cm
  perf_cm@model$confusion
  if (max(abs(cm[1:9] - perf_cm@model$confusion[1:9])) > 2) stop("cm is wrong")

  
}

h2oTest.doTest("Test H2O N-Fold CV", test.h2o.nfold)
