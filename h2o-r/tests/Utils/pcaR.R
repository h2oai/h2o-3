# Check each principal component (eigenvector) equal up to a sign flip
checkSignedCols <- function(object, expected, tolerance = 1e-6) {
  expect_equal(dim(object), dim(expected))
  isFlipped <- rep(FALSE, ncol(object))
  
  for(j in 1:ncol(object)) {
    # isFlipped[j] <- abs(object[1,j] - expected[1,j]) > tolerance
    isFlipped[j] <- abs(object[1,j] - expected[1,j]) > abs(object[1,j] + expected[1,j])
    # if(isFlipped[j])
    #  expect_equal(-object[,j], expected[,j], tolerance = tolerance)
    # else
    #  expect_equal(object[,j], expected[,j], tolerance = tolerance)
    mult <- ifelse(isFlipped[j], -1, 1)
    for(i in 1:nrow(object))
      expect_equal(mult*object[i,j], expected[i,j], tolerance = tolerance, scale = 1)
  }
  return(isFlipped)
}

checkPCAModel <- function(fitH2O, fitR, tolerance = 1e-6) {
  pcimpR <- summary(fitR)$importance
  pcimpH2O <- fitH2O@model$pc_importance
  Log.info("Compare Importance between R and H2O\n")
  Log.info("R Importance of Components:"); print(pcimpR)
  Log.info("H2O Importance of Components:"); print(pcimpH2O)
  expect_equal(dim(pcimpH2O), dim(pcimpR))
  pcimpH2O <- as.matrix(pcimpH2O); dimnames(pcimpH2O) <- dimnames(pcimpR)
  expect_equal(pcimpH2O, pcimpR, tolerance = tolerance, scale = 1)
  
  Log.info("Compare Principal Components between R and H2O\n") 
  Log.info("R Principal Components:"); print(fitR$rotation)
  Log.info("H2O Principal Components:"); print(fitH2O@model$eigenvectors)
  checkSignedCols(as.matrix(fitH2O@model$eigenvectors), fitR$rotation, tolerance = tolerance)
}
