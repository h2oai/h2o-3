setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.GBM.ecology.random.noise <- function() {
  df <- h2o.uploadFile(locate("smalldata/gbm_test/ecology_model.csv"))
  model <- h2o.gbm(x = 3:13, y = "Angaus", training_frame = df, pred_noise_bandwidth=0.5)
  print(model)
}

doTest("GBM: Ecology Data", test.GBM.ecology.random.noise)

