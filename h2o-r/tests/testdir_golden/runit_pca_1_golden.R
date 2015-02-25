setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# Compare within-cluster sum of squared error
test.pcavanilla.golden <- function(H2Oserver) {
  # Import data: 
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(H2Oserver, locate("smalldata/pca_test/USArrests.csv"), key = "arrestsH2O")
  
  Log.info("Compare with PCA when center = TRUE, scale. = FALSE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = FALSE)
  fitH2O <- h2o.prcomp(arrestsH2O, k = 4, gamma = 0, init = "PlusPlus", center = TRUE, scale. = FALSE)
  
  sdevR <- fitR$sdev
  sdevH2O <- fitH2O@model$std_deviation
  Log.info(paste("H2O Std Dev : ", sdevH2O, "\t\t", "R Std Dev : ", sdevR))
  Log.info("Compare Standard Deviations between R and H2O\n") 
  expect_equal(fitH2O@model$std_deviation, fitR$sdev, tolerance = 1e-6)
  
  # Check each principal component (eigenvector) equal up to a sign flip
  expect_equal_eigvec <- function(object, expected, tolerance = 0) {
    for(j in 1:ncol(object)) {
      isFlipped <- abs(object[1,j] - expected[1,j]) > tolerance
      if(isFlipped)
        expect_equal(-object[,j], expected[,j], tolerance = tolerance)
      else
        expect_equal(object[,j], expected[,j], tolerance = tolerance)
    }
  }
  Log.info("R Principal Components:"); print(fitR$rotation)
  Log.info("H2O Principal Components:"); print(fitH2O@model$eigenvectors)
  Log.info("Compare Principal Components between R and H2O\n") 
  expect_equal_eigvec(as.matrix(fitH2O@model$eigenvectors), fitR$rotation, tolerance = 1e-6)
  
  pcimpR <- summary(fitR)$importance
  pcimpH2O <- fitH2O@model$pc_importance
  Log.info("R Importance of Components:"); print(pcimpR)
  Log.info("H2O Importance of Components:"); print(pcimpH2O)
  Log.info("Compare Importance between R and H2O\n") 
  expect_equal(as.matrix(pcimpH2O), pcimpR, tolerance = 1e-6)
  
  testEnd()
}

doTest("PCA Golden Test: USArrests with Centering", test.pcavanilla.golden)