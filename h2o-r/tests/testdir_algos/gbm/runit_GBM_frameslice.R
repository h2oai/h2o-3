setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.frameslice <- function() {
  h2oTest.logInfo("Importing prostate data...\n")
  pros.hex <- h2o.importFile(path = h2oTest.locate("smalldata/logreg/prostate.csv"))

  h2oTest.logInfo("Running GBM on a sliced data frame...\n")
  pros.hex[,2] = as.factor(pros.hex[,2])
  pros.gbm <- h2o.gbm(x = 2:8, y = 1, training_frame = pros.hex[, 2:9], distribution = "bernoulli")

  
}

h2oTest.doTest("GBM Test: Model building on sliced h2o frame", test.GBM.frameslice)
