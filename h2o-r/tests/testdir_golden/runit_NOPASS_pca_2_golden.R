setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.pcastand.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/pca_test/USArrests.csv"), key = "arrestsH2O")
  
  Log.info("Compare with PCA when center = TRUE, scale. = TRUE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = TRUE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, gamma = 0, init = "PlusPlus", center = TRUE, scale. = TRUE)
  checkPCAModel(fitH2O, fitR, tolerance = 1e-6)
  
#   pcimpR <- summary(fitR)$importance
#   pcimpH2O <- fitH2O@model$pc_importance
#   Log.info("R Importance of Components:"); print(pcimpR)
#   Log.info("H2O Importance of Components:"); print(pcimpH2O)
#   Log.info("Compare Importance between R and H2O\n") 
#   expect_equal(as.matrix(pcimpH2O), pcimpR, tolerance = 1e-6)
  
  testEnd()
}

doTest("PCA Golden Test: USArrests with Standardization", test.pcastand.golden)