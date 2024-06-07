setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1383 <- function() {
  k <- 10
  Log.info("Importing fgl_tr.csv...")
  fgl.dat <- read.csv(locate("smalldata/pca_test/fgl_tr.csv"), stringsAsFactors = TRUE)
  fgl.hex <- h2o.importFile(locate("smalldata/pca_test/fgl_tr.csv"))
  print(summary(fgl.hex))
  
  Log.info("Reshuffling R data to match H2O...")
  fgl.mm <- alignData(fgl.dat, center = TRUE, scale = TRUE, use_all_factor_levels = FALSE)
  print(summary(fgl.mm))
  
  Log.info("Building PCA model...")
  fitR <- prcomp(fgl.mm, center = FALSE, scale. = FALSE)
  fitH2O <- h2o.prcomp(fgl.hex, k = k, transform = "STANDARDIZE", max_iterations = 5000, use_all_factor_levels = FALSE)
  
  Log.info("R Eigenvectors:"); print(fitR$rotation[,1:k])
  Log.info("H2O Eigenvectors:"); print(fitH2O@model$eigenvectors)
  eigvecR <- as.matrix(fitR$rotation[,1:k])
  eigvecH2O <- as.matrix(fitH2O@model$eigenvectors)
  dimnames(eigvecH2O) <- dimnames(eigvecR)   # Since H2O row names don't match R
  checkSignedCols(eigvecH2O, eigvecR, tolerance = 1e-5)
  
  impR <- summary(fitR)$importance
  impH2O <- fitH2O@model$importance
  Log.info("R PC Importance:"); print(impR[,1:k])
  Log.info("H2O PC Importance:"); print(impH2O)
  expect_equal(as.numeric(impR[1,1:k]), as.numeric(impH2O[1,]), tolerance = 1e-6)
  
  
}

doTest("PUBDEV-1383: Compare numerical accuracy of H2O and R PCA", test.pubdev.1383)
