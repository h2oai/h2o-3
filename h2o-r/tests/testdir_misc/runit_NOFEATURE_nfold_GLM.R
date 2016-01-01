setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.h2o.nfold_GLM <- function() {
  tolerance <- 1e-4

  conn = h2o.init()
  hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/logreg/prostate.csv")))
  predictors = c(3:9)
  response = 2
  NFOLDS = 4

  # let GLM do the n-fold CV
  m <- h2o.glm(x = predictors, y = response, training_frame = hex, nfolds=NFOLDS, family="binomial")

  # now do the same by hand
  # H2O doesn't support writes into a row range at this point, do in R instead for simplicity
  predictions = numeric(length = nrow(hex))

  hex$fold_id <- rep(seq(NFOLDS), nrow(hex)/NFOLDS)

  for (i in 1:NFOLDS) {
    train <- hex[hex$fold_id!=i,]
    valid <- hex[hex$fold_id==i,]
    model <- h2o.glm(x = predictors, y = response, training_frame = train, family="binomial")
    pred <- h2o.predict(model, valid)
    len <- nrow(valid)
    pred.R <- as.data.frame(pred[,3])
    predictions[as.matrix(as.data.frame(valid$ID))] <- as.matrix(pred.R)
  }

  # compare metrics
  perf <- h2o.performance(as.h2o(predictions), hex[,response])
  auc <- m@model$auc
  accuracy <- m@model$accuracy
  cm <- m@model$confusion

  auc
  perf@model$auc
  if (abs(auc - perf@model$auc) > tolerance) stop("AUC is wrong")

  accuracy
  perf@model$accuracy
  if (abs(accuracy - perf@model$accuracy) > tolerance) stop("accuracy is wrong")

  cm
  perf@model$confusion
  if (max(abs(cm[1:9] - perf@model$confusion[1:9])) != 0) stop("cm is wrong")

  
}

h2oTest.doTest("Test H2O N-Fold CV for GLM", test.h2o.nfold_GLM)
