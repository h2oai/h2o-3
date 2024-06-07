setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Make sure we can run with airline data
test.pca.airline<- function() {
  dimD = 234
  pp = h2o.uploadFile(locate("smalldata/airlines/AirlinesTest.csv.zip"))
  aa = h2o.prcomp(pp, k=dimD, transform="STANDARDIZE")

  dd = h2o.uploadFile(locate("smalldata/airlines/AirlinesTrain.csv.zip"))
  predH2O <- predict(aa, newdata=dd)

  expect_true(h2o.ncol(predH2O)==dimD)   # projected data should have same column as dimD
}

doTest("PCA Test: Airlines Data", test.pca.airline)
