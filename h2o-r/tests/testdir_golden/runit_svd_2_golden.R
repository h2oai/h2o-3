setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.svdidentity.golden <- function() {
  Log.info("Importing USArrests.csv data...") 
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  Log.info("Compute SVD with nv = 4 eigenvectors")
  fitH2O <- h2o.svd(arrestsH2O, nv = 4, transform = "NONE")
  Log.info("Singular values (D)"); print(fitH2O@model$d)
  Log.info("Right singular vectors (V)"); print(fitH2O@model$v)
  
  Log.info("Check identity Av = dv (A = data, v = eigenvector, d = eigenvalue)")
  arrestsH2O.mat <- as.matrix(arrestsH2O)
  gram <- t(arrestsH2O.mat) %*% arrestsH2O.mat
  eigvec <- fitH2O@model$v
  eigval <- fitH2O@model$d^2
  for(i in 1:length(eigval))
    expect_equal(as.numeric(gram %*% eigvec[,i]), eigval[i] * eigvec[,i], tolerance = 1e-5)
  
  testEnd()
}

doTest("SVD Golden Eigenvector/Eigenvalue Identity Test: USArrests", test.svdidentity.golden)
