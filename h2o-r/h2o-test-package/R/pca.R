# Check each principal component (eigenvector) equal up to a sign flip
h2oTest.checkSignedCols <- function(object, expected, tolerance = 1e-6) {
  expect_equal(dim(object), dim(expected))
  
  is_flipped <- sapply(1:ncol(object), function(j) {
    flipped <- abs(object[,j] - expected[,j]) > abs(object[,j] + expected[,j])
    num_true <- length(which(flipped))
    num_false <- length(which(!flipped))
    if(num_true == num_false) return(runif(1) >= 0.5)
    num_true > num_false
  })
  
  mult <- ifelse(is_flipped, -1, 1)
  sapply(1:ncol(object), function(j) {
    expect_equal(mult[j] * object[,j], expected[,j], tolerance = tolerance, scale = 1)
  })
  return(is_flipped)
}

h2oTest.checkPCAModel <- function(fitH2O, fitR, tolerance = 1e-6, sort_rows = TRUE) {
  k <- fitH2O@parameters$k
  pcimpR <- summary(fitR)$importance
  pcimpH2O <- fitH2O@model$importance
  eigvecR <- fitR$rotation
  eigvecH2O <- fitH2O@model$eigenvectors
  
  pcimpR <- pcimpR[,1:k]
  eigvecR <- eigvecR[,1:k]
  
  if(sort_rows && !is.null(rownames(eigvecH2O)) && !is.null(rownames(eigvecR))) {
    Log.info("Sorting rows alphabetically by row name")
    eigvecH2O <- eigvecH2O[order(rownames(eigvecH2O)),]
    eigvecR <- eigvecR[order(rownames(eigvecR)),]
    expect_equal(dim(eigvecH2O), dim(eigvecR))
    expect_true(all(rownames(eigvecH2O) == rownames(eigvecR)))
  }
  
  if(k == 1) {
    eigvecH2O <- as.matrix(eigvecH2O)
    eigvecR <- as.matrix(eigvecR)
    pcimpR <- as.matrix(pcimpR)
    colnames(eigvecH2O) <- "PC1"
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
  h2oTest.checkSignedCols(as.matrix(eigvecH2O), eigvecR, tolerance = tolerance)
}
