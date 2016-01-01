setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Generate lots of keys then remove them
##




test <- function() {
  arrests.hex = h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), "arrests.hex")

  h2oTest.logInfo("Slicing column 1 of arrests 50 times")
  for(i in 1:50) {
    arrests.hex[,1]
    if( i %% 50 == 0 ) h2oTest.logInfo(paste("Finished ", paste(i, " slices of arrests.hex", sep = ""), sep = ""))
  }

  h2oTest.logInfo("Performing 100 PCA's on the arrests data")
  for(i in 1:100) {
    arrests.pca.h2o = h2o.prcomp(arrests.hex, k = 2)
    if( i %% 50 == 0 ) h2oTest.logInfo(paste("Finished ", paste(i, " PCAs of arrests.hex", sep = ""), sep = ""))
  }
  h2oTest.logInfo("Making a call to remove all")
  h2o.removeAll()

  h2oTest.logInfo("h2o.ls() should return an empty list")
  expect_equal(length(h2o.ls()$key), 0)

  
}

h2oTest.doTest("Many Keys Test: Removing", test)

