setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.svd.golden <- function() {
  # Import data: 
  h2oTest.logInfo("Importing USArrests.csv data...") 
  arrestsR <- read.csv(h2oTest.locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  
  h2oTest.logInfo("Compare with SVD")
  fitR <- svd(arrestsR, nv = 4)
  fitH2O <- h2o.svd(arrestsH2O, nv = 4, transform = "NONE", max_iterations = 2000)
  
  h2oTest.logInfo("Compare singular values (D)")
  h2oTest.logInfo(paste("R Singular Values:", paste(fitR$d, collapse = ", ")))
  h2oTest.logInfo(paste("H2O Singular Values:", paste(fitH2O@model$d, collapse = ", ")))
  expect_equal(fitH2O@model$d, fitR$d, tolerance = 1e-6)
  
  h2oTest.logInfo("Compare right singular vectors (V)")
  vH2O <- h2o.getFrame(fitH2O@model$v_key$name)
  vH2O.mat <- as.data.frame(vH2O)
  h2oTest.logInfo("R Right Singular Vectors"); print(fitR$v)
  h2oTest.logInfo("H2O Right Singular Vectors"); print(vH2O.mat)
  isFlipped1 <- h2oTest.checkSignedCols(vH2O.mat, fitR$v, tolerance = 1e-5)
  
  h2oTest.logInfo("Compare left singular vectors (U)")
  uH2O <- h2o.getFrame(fitH2O@model$u_key$name)
  uH2O.mat <- as.matrix(uH2O)
  h2oTest.logInfo("R Left Singular Vectors:"); print(head(fitR$u))
  h2oTest.logInfo("H2O Left Singular Vectors:"); print(head(uH2O.mat))
  isFlipped2 <- h2oTest.checkSignedCols(uH2O.mat, fitR$u, tolerance = 5e-5)
  expect_equal(isFlipped1, isFlipped2)
  
  
}

h2oTest.doTest("SVD Golden Test: USArrests", test.svd.golden)
