setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pcascore.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/pca_test/USArrests.csv"), key = "arrestsH2O")
  
  Log.info("Compare with PCA when center = TRUE, scale. = FALSE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = FALSE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, gamma = 0, init = "PlusPlus", center = TRUE, scale. = FALSE)
  checkPCAModel(fitH2O, fitR, tolerance = 1e-6)
  
  Log.info("Compare Projections into PC space")
  predR <- predict(fitR, arrestsR)
  predH2O <- predict(fitH2O, arrestsH2O)
  Log.info("R Projection:"); print(head(predR))
  Log.info("H2O Projection:"); print(head(predH2O))
  checkSignedCols(as.matrix(predH2O), predR, tolerance = 1e-6)
  
  testEnd()
}

doTest("PCA Golden Test: USArrests with Scoring", test.pcascore.golden)