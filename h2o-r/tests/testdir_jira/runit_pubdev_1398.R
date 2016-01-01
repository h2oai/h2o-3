setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1398 <- function() {
  k <- 13
  h2oTest.logInfo("Importing decathlon.csv...")
  dec.dat <- read.csv(h2oTest.locate("smalldata/pca_test/decathlon.csv"))
  dec.hex <- h2o.importFile(h2oTest.locate("smalldata/pca_test/decathlon.csv"))
  print(summary(dec.hex))
  
  h2oTest.logInfo("Reshuffling R data to match H2O...")
  dec.mm <- h2oTest.alignData(dec.dat, center = TRUE, scale = TRUE, use_all_factor_levels = FALSE)
  print(summary(dec.mm))
  
  h2oTest.logInfo("Building PCA model...")
  fitR <- prcomp(dec.mm, center = FALSE, scale. = FALSE)
  fitH2O <- h2o.prcomp(dec.hex, k = k, transform = "STANDARDIZE", max_iterations = 5000, use_all_factor_levels = FALSE)
  
  h2oTest.logInfo("R Eigenvectors:"); print(fitR$rotation[,1:k])
  h2oTest.logInfo("H2O Eigenvectors:"); print(fitH2O@model$eigenvectors)
  eigvecR <- as.matrix(fitR$rotation[,1:k])
  eigvecH2O <- as.matrix(fitH2O@model$eigenvectors)
  dimnames(eigvecH2O) <- dimnames(eigvecR)   # Since H2O row names don't match R
  h2oTest.checkSignedCols(eigvecH2O, eigvecR, tolerance = 1e-5)
  
  h2oTest.logInfo("Predicting on PCA model...")
  predR <- predict(fitR)
  predH2O <- predict(fitH2O, dec.hex)
  h2oTest.logInfo("R Predictions:"); print(head(predR))
  h2oTest.logInfo("H2O Predictions:"); print(head(predH2O))
  h2oTest.checkSignedCols(as.data.frame(predH2O), predR[,1:k], tolerance = 2e-5)
  
  
}

h2oTest.doTest("PUBDEV-1398: Compare projections of R and H2O PCA", test.pubdev.1398)
