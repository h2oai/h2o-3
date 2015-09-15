setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.frameslice <- function() {
  Log.info("Importing prostate data...\n")
  pros.hex <- h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))

  Log.info("Running GBM on a sliced data frame...\n")
  pros.hex[,2] = as.factor(pros.hex[,2])
  pros.gbm <- h2o.gbm(x = 2:8, y = 1, training_frame = pros.hex[, 2:9], distribution = "bernoulli")

  testEnd()
}

doTest("GBM Test: Model building on sliced h2o frame", test.GBM.frameslice)
