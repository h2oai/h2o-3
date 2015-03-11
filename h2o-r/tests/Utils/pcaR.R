# Check each principal component (eigenvector) equal up to a sign flip
checkSignedCols <- function(object, expected, tolerance = 1e-6) {
  expect_equal(dim(object), dim(expected))
  isFlipped <- rep(FALSE, ncol(object))
  
  for(j in 1:ncol(object)) {
    isFlipped[j] <- abs(object[1,j] - expected[1,j]) > tolerance
    if(isFlipped[j])
      expect_equal(-object[,j], expected[,j], tolerance = tolerance)
    else
      expect_equal(object[,j], expected[,j], tolerance = tolerance)
  }
}

checkPCAModel <- function(fitH2O, fitR, tolerance = 1e-6) {
  sdevR <- fitR$sdev
  sdevH2O <- fitH2O@model$std_deviation
  Log.info("Compare Standard Deviations between R and H2O\n") 
  Log.info(paste("H2O Std Dev : ", sdevH2O, "\t\t", "R Std Dev : ", sdevR))
  expect_equal(fitH2O@model$std_deviation, fitR$sdev, tolerance = tolerance)
  
  Log.info("Compare Principal Components between R and H2O\n") 
  Log.info("R Principal Components:"); print(fitR$rotation)
  Log.info("H2O Principal Components:"); print(fitH2O@model$eigenvectors)
  checkSignedCols(as.matrix(fitH2O@model$eigenvectors), fitR$rotation, tolerance = tolerance)
}
