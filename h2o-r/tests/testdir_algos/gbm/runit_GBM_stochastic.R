setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM <- function() {
  df <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"), destination_frame="prostate.hex")

  ## AGE Regression
  m1 <- h2o.gbm(x=4:8,y="AGE",training_frame=df, seed=123, nfold=5)
  print(h2o.mse(m1, xval=T))
  m2 <- h2o.gbm(x=4:8,y="AGE",training_frame=df, seed=123, nfold=5, sample_rate=0.3, col_sample_rate=0.3)
  print(h2o.mse(m2, xval=T))
  expect_true(h2o.mse(m2, xval=T) < h2o.mse(m1, xval=T), "GBM with stochastic sampling should have lower cross-val MSE!")

  ## CAPSULE Binary Classification (GBM Bernoulli)
  m1 <- h2o.gbm(x=3:8,y="CAPSULE",training_frame=df, seed=123, nfold=5)
  print(h2o.mse(m1, xval=T))
  m2 <- h2o.gbm(x=3:8,y="CAPSULE",training_frame=df, seed=123, nfold=5, sample_rate=0.3, col_sample_rate=0.3)
  print(h2o.mse(m2, xval=T))
  expect_true(h2o.mse(m2, xval=T) < h2o.mse(m1, xval=T), "GBM with stochastic sampling should have lower cross-val MSE!")
}
h2oTest.doTest("GBM Test: offset", test.GBM)
