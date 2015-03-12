setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.frameslice <- function(conn) {
  Log.info("Importing prostate data...\n")
  pros.hex <- h2o.importFile(conn, path = locate("smalldata/logreg/prostate.csv"))

  Log.info("Running GBM on a sliced data frame...\n")
  pros.gbm <- h2o.gbm(x = 2:8, y = 1, training_frame = pros.hex[, 2:9], loss = "bernoulli")

  testEnd()
}

doTest("GBM Test: Model building on sliced h2o frame", test.GBM.frameslice)