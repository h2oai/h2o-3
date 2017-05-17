# Check each principal component (eigenvector) equal up to a sign flip
checkSignedCols <- function(object, expected, tolerance = 1e-6) {
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
    Log.info(paste("Maximum difference for eigvector ", j, " is:", max(abs(mult[j] * object[,j]-expected[,j])), sep=" "))
    expect_equal(mult[j] * object[,j], expected[,j], tolerance=tolerance, scale=1)
  })
  return(is_flipped)
}
checkPCAModel<- function(fitH2O, fitR, tolerance=1e-6, sort_rows=TRUE, compare_all_importance=TRUE) {
  k <- fitH2O@parameters$k
  pcimpR <- summary(fitR)$importance
  pcimpH2O <- fitH2O@model$importance
  eigvecR <- fitR$rotation
  eigvecH2O <- fitH2O@model$eigenvectors
  textHeader = "Compare Importance between R and H2O\n"
  RText = "R Importance of Components:"
  H2OText = "H2O Importance of Components:"

  checkPCAModelWork(k, pcimpR, pcimpH2O, eigvecR, eigvecH2O, textHeader, RText, H2OText, tolerance, sort_rows=TRUE, compare_all_importance=TRUE)
}

checkPCAModelWork <- function(k, pcimpR, pcimpH2O, eigvecR, eigvecH2O, textHeader, RImportanceHeader, H2OImportanceHeader, tolerance=1e-6, sort_rows=TRUE, compare_all_importance=TRUE) {
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
  
  Log.info(textHeader)
  Log.info(RImportanceHeader); print(pcimpR)
  Log.info(H2OImportanceHeader); print(pcimpH2O)
  expect_equal(dim(pcimpH2O), dim(pcimpR))

  dimnames(pcimpH2O) <- dimnames(pcimpR)
  pcimpH2O <- as.matrix(pcimpH2O)
  pcimpR <- as.matrix(pcimpR)
  if (compare_all_importance) { # compare all: Standard deviation, Proportion of Variance and Cumulative Proportion
    Log.info(paste("Maximum difference for your importance comparisons is:", max(abs(pcimpH2O-pcimpR)), sep=" "))
    expect_equal(pcimpH2O, pcimpR, tolerance = tolerance, scale = 1)
  } else {  # only compare Standard deviation (the actual eigenvalues)
    for (ind in 1:dim(pcimpH2O)[2]) {
      expect_equal(pcimpH2O[1,ind], pcimpR[1,ind], tolerance=tolerance)
    }
  }

  Log.info("Compare Principal Components between R and H2O\n") 
  Log.info("R Principal Components:"); print(eigvecR)
  Log.info("H2O Principal Components:"); print(eigvecH2O)
  checkSignedCols(as.matrix(eigvecH2O), as.matrix(eigvecR), tolerance = tolerance)
}

# generate the reconstructed datasets using the PCA eigenvectors and the projected datasets with numerical datas only
# The generated dataset will need to be destandardized if we want to compare it to the original dataset
genReconstructedData <- function(eigenVectors, predMatrix) {
  dimM = dim(predMatrix)   # should be size m by k
  dimE = dim(eigenVectors)    # should be n by k

  expect_equal(dimM[2], dimE[2])
  predMatrix <- as.matrix(predMatrix)
  eigenVectors <- as.matrix(eigenVectors)
  eigenVectorsT <- t(eigenVectors)

  outMat = predMatrix %*% eigenVectorsT
}