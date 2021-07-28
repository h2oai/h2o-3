setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.h <- function() {
  prostate.hex <- h2o.importFile(locate("smalldata/logreg/prostate_train.csv"), destination_frame="prostate.hex")
  prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
  prostate.h2o <- h2o.gbm(seed = 1234, x = 2:9, y = "CAPSULE", training_frame = prostate.hex, distribution = "bernoulli", ntrees = 100, max_depth = 5, min_rows = 10, learn_rate = 0.1)
  hval <- h2o.h(prostate.h2o, prostate.hex, c('DPROS','DCAPS'))
  expect_equal(hval, 0.17328, tolerance=1e-5)
}

doTest("GBM Test: h statistic", test.GBM.h)
