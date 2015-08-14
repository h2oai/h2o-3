setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1660 <- function(conn) {
  Log.info("Importing USArrests.csv data...")
  arrests.hex <- h2o.importFile(locate("smalldata/pca_test/USArrests.csv"))
  
  Log.info("Building PCA model with max_iterations = 2000")
  fitH2O <- h2o.prcomp(arrests.hex, k = 4, transform = "NONE", max_iterations = 2000)
  
  Log.info("Extract first 3 eigenvectors from H2OTable")
  eigvec <- fitH2O@model$eigenvectors[,1:3]
  print(eigvec)
  testEnd()
}

doTest("PUBDEV-1660: Slicing and printing a subset of H2OTable", test.pubdev.1660)
