setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.svd.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/pca_test/USArrests.csv"), key = "arrestsH2O")
  
  Log.info("Compare with SVD when center = FALSE, scale. = FALSE")
  fitR <- svd(arrestsR, nv = 4)
  fitH2O <- h2o.svd(arrestsH2O, nv = 4, center = FALSE, scale. = FALSE)
  
  expect_equal(fitH2O@model$d, fitR$d, tolerance = 1e-6)
  checkSignedCols(fitH2O@model$v, fitR$v)
  testEnd()
}

doTest("SVD Golden Test: USArrests", test.svd.golden)