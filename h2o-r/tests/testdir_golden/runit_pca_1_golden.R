setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pcavanilla.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing USArrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  Log.info("Compare with PCA when center = TRUE, scale. = TRUE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = TRUE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = 'STANDARDIZE', max_iterations = 2000)
  checkPCAModel(fitH2O, fitR, tolerance = 1e-5) 
  
  Log.info("Compare with PCA when center = TRUE, scale. = FALSE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = FALSE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, transform = 'DEMEAN', max_iterations = 2000)
  checkPCAModel(fitH2O, fitR, tolerance = 1e-5)
  testEnd()
}

doTest("PCA Golden Test: USArrests with Transformed Data", test.pcavanilla.golden)
