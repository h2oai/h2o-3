setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1660 <- function() {
  h2oTest.logInfo("Importing USArrests.csv data...")
  arrests.hex <- h2o.importFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"))
  
  h2oTest.logInfo("Building PCA model with max_iterations = 2000")
  fitH2O <- h2o.prcomp(arrests.hex, k = 4, transform = "NONE", max_iterations = 2000)
  
  h2oTest.logInfo("Extract first 3 eigenvectors from H2OTable")
  eigvec <- fitH2O@model$eigenvectors[,1:3]
  print(eigvec)
  
}

h2oTest.doTest("PUBDEV-1660: Slicing and printing a subset of H2OTable", test.pubdev.1660)
