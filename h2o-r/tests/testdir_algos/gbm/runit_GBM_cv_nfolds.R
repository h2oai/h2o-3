setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.nfolds <- function() {
  prostate.hex <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  print(summary(prostate.hex))
  prostate.hex[,2] <- as.factor(prostate.hex[,2])
  prostate.nfolds <- h2o.gbm.cv(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, distribution = "bernoulli")
  print(prostate.nfolds)
  prostate.nfolds.real <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, distribution = "bernoulli")
  print(prostate.nfolds.real)
  valid.and.nfolds <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, validation_frame = prostate.hex, distribution = "bernoulli")
  print(valid.and.nfolds)
  valid.and.nfolds.preds <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, validation_frame = prostate.hex, distribution = "bernoulli",
                                    keep_cross_validation_models=T, keep_cross_validation_predictions=T)
  print(valid.and.nfolds.preds)
  print(h2o.getModel(valid.and.nfolds.preds@model$cross_validation_models[[4]]$name))
  print(h2o.getFrame(valid.and.nfolds.preds@model$cross_validation_predictions[[4]]$name))
  
}

doTest("GBM Cross-Validation Test: Prostate", test.GBM.nfolds)
