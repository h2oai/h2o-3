# Check each principal component (eigenvector) equal up to a sign flip
checkSignedCols <- function(object, expected, tolerance = 1e-6) {
  expect_equal(dim(object), dim(expected))
  isFlipped <- rep(FALSE, ncol(object))
  
  for(j in 1:ncol(object)) {
    # isFlipped[j] <- abs(object[1,j] - expected[1,j]) > tolerance
    isFlipped[j] <- abs(object[1,j] - expected[1,j]) > abs(object[1,j] + expected[1,j])
    mult <- ifelse(isFlipped[j], -1, 1)
    for(i in 1:nrow(object))
      expect_equal(mult*object[i,j], expected[i,j], tolerance = tolerance, scale = 1)
  }
  return(isFlipped)
}

checkPCAModel <- function(fitH2O, fitR, tolerance = 1e-6) {
  k <- fitH2O@parameters$k
  pcimpR <- summary(fitR)$importance
  pcimpH2O <- fitH2O@model$pc_importance
  eigvecR <- fitR$rotation
  eigvecH2O <- fitH2O@model$eigenvectors
  
  pcimpR <- pcimpR[,1:k]
  eigvecR <- eigvecR[,1:k]
  if(k == 1) {
    eigvecR <- as.matrix(eigvecR)
    pcimpR <- as.matrix(pcimpR)
    colnames(eigvecR) <- "PC1"
    colnames(pcimpR) <- "PC1"
  }
  
  Log.info("Compare Importance between R and H2O\n")
  Log.info("R Importance of Components:"); print(pcimpR)
  Log.info("H2O Importance of Components:"); print(pcimpH2O)
  expect_equal(dim(pcimpH2O), dim(pcimpR))
  pcimpH2O <- as.matrix(pcimpH2O); dimnames(pcimpH2O) <- dimnames(pcimpR)
  expect_equal(pcimpH2O, pcimpR, tolerance = tolerance, scale = 1)
  
  Log.info("Compare Principal Components between R and H2O\n") 
  Log.info("R Principal Components:"); print(eigvecR)
  Log.info("H2O Principal Components:"); print(eigvecH2O)
  checkSignedCols(as.matrix(eigvecH2O), eigvecR, tolerance = tolerance)
}
