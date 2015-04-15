setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pca.score <- function(H2Oserver) {
  Log.info("Importing arrests.csv data...") 
  arrestsH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/pca_test/USArrests.csv"), key = "arrestsH2O")
  
  Log.info("Run PCA with init = 'PlusPlus', center = TRUE, scale. = FALSE")
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, gamma = 0, init = "PlusPlus", center = TRUE, scale. = FALSE)
  print(fitH2O)
  
  Log.info("Project training data into eigenvector subspace")
  predH2O <- predict(fitH2O, arrestsH2O)
  Log.info("H2O Projection:"); print(head(predH2O))
  testEnd()
}

doTest("PCA Test: USArrests with Scoring", test.pca.score)
