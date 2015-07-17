setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.nfolds <- function(conn) {
  prostate.hex <- h2o.uploadFile(conn, locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  print(summary(prostate.hex))
  prostate.hex[,2] <- as.factor(prostate.hex[,2])
  prostate.nfolds <- h2o.gbm.cv(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, distribution = "bernoulli")
  print(prostate.nfolds)
  prostate.nfolds.real <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, distribution = "bernoulli")
  print(prostate.nfolds.real)
  valid.and.nfolds <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, validation_frame = prostate.hex, distribution = "bernoulli")
  print(valid.and.nfolds)
  testEnd()
}

doTest("GBM Cross-Validation Test: Prostate", test.GBM.nfolds)
