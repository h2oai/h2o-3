setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.nfolds <- function(conn) {
  prostate.hex <- h2o.uploadFile(conn, locate("smalldata/logreg/prostate.csv"), key = "prostate.hex")
  print(summary(prostate.hex))
  prostate.nfolds <- h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5)
  print(prostate.nfolds)

  # Can't specify both nfolds >= 2 and validation = H2OParsedData at once
  expect_error(h2o.gbm(y = 2, x = 3:9, training_frame = prostate.hex, nfolds = 5, validation_frame = prostate.hex))


  m <- h2o.getModel(conn, prostate.nfolds@key)
    
  print(m)

  predict(m, prostate.hex)

  testEnd()
}

doTest("GBM Cross-Validation Test: Prostate", test.GBM.nfolds)
