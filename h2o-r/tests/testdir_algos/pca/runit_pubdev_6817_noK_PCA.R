setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# Test PCA on USArrests.csv
test.pca.arrests <- function() {
  Log.info("Importing USArrests.csv data...\n")
  arrests.hex <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"))
  arrests.pca.h2o <- h2o.prcomp(training_frame = arrests.hex, k = 1, seed=12345)
  pca_noK <- h2o.prcomp(training_frame = arrests.hex, seed=12345)
  
  pred1 <- h2o.predict(arrests.pca.h2o, arrests.hex)
  pred2 <- h2o.predict(pca_noK, arrests.hex)
  compareFrames(pred1, pred2)
}

doTest("PUBDEV-6817: PCA Test: check model when k is not specified", test.pca.arrests)
