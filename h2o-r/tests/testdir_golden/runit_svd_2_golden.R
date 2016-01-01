setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.svdidentity.golden <- function() {
  h2oTest.logInfo("Importing USArrests.csv data...") 
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  h2oTest.logInfo("Compute SVD with nv = 4 eigenvectors")
  fitH2O <- h2o.svd(arrestsH2O, nv = 4, transform = "NONE")
  vH2O <- h2o.getFrame(fitH2O@model$v_key$name)
  vH2O.mat <- as.matrix(vH2O)
  h2oTest.logInfo("Singular values (D)"); print(fitH2O@model$d)
  h2oTest.logInfo("Right singular vectors (V)"); print(vH2O.mat)
  
  h2oTest.logInfo("Check identity Av = dv (A = data, v = eigenvector, d = eigenvalue)")
  arrestsH2O.mat <- as.matrix(arrestsH2O)
  gram <- t(arrestsH2O.mat) %*% arrestsH2O.mat
  eigvec <- vH2O.mat
  eigval <- fitH2O@model$d^2
  for(i in 1:length(eigval))
    expect_equal(as.numeric(gram %*% eigvec[,i]), eigval[i] * eigvec[,i], tolerance = 1e-5)
  
  
}

h2oTest.doTest("SVD Golden Eigenvector/Eigenvalue Identity Test: USArrests", test.svdidentity.golden)
