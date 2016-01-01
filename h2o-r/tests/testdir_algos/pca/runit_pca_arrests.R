setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Test PCA on USArrests.csv
test.pca.arrests <- function() {
  h2oTest.logInfo("Importing USArrests.csv data...\n")
  arrests.hex <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"))
  arrests.sum <- summary(arrests.hex)
  print(arrests.sum)

  for(i in 1:4) {
    h2oTest.logInfo(paste("H2O PCA with ", i, " dimensions:\n", sep = ""))
    h2oTest.logInfo(paste("Using these columns: ", colnames(arrests.hex)))
    arrests.pca.h2o <- h2o.prcomp(training_frame = arrests.hex, k = as.numeric(i))
    print(arrests.pca.h2o)
  }
  
  
}

h2oTest.doTest("PCA Test: USArrests Data", test.pca.arrests)
