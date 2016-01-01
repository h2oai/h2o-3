setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.pca.score <- function() {
  h2oTest.logInfo("Importing arrests.csv data...") 
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  h2oTest.logInfo("Run PCA with transform = 'DEMEAN'")
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = "DEMEAN")
  print(fitH2O)
  
  h2oTest.logInfo("Project training data into eigenvector subspace")
  predH2O <- predict(fitH2O, arrestsH2O)
  h2oTest.logInfo("H2O Projection:"); print(head(predH2O))
  
}

h2oTest.doTest("PCA Test: USArrests with Scoring", test.pca.score)
