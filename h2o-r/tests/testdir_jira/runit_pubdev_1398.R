setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1398 <- function() {
  k <- 13
  Log.info("Importing decathlon.csv...")
  dec.dat <- read.csv(locate("smalldata/pca_test/decathlon.csv"), stringsAsFactors = TRUE)
  dec.hex <- h2o.importFile(locate("smalldata/pca_test/decathlon.csv"))
  print(summary(dec.hex))
  
  Log.info("Reshuffling R data to match H2O...")
  dec.mm <- alignData(dec.dat, center = TRUE, scale = TRUE, use_all_factor_levels = FALSE)
  print(summary(dec.mm))
  
  Log.info("Building PCA model...")
  fitR <- prcomp(dec.mm, center = FALSE, scale. = FALSE)
  fitH2O <- h2o.prcomp(dec.hex, k = k, transform = "STANDARDIZE", max_iterations = 5000, use_all_factor_levels = FALSE)
  
  Log.info("R Eigenvectors:"); print(fitR$rotation[,1:k])
  Log.info("H2O Eigenvectors:"); print(fitH2O@model$eigenvectors)
  eigvecR <- as.matrix(fitR$rotation[,1:k])
  eigvecH2O <- as.matrix(fitH2O@model$eigenvectors)
  dimnames(eigvecH2O) <- dimnames(eigvecR)   # Since H2O row names don't match R
  checkSignedCols(eigvecH2O, eigvecR, tolerance = 1e-5)
  
  Log.info("Predicting on PCA model...")
  predR <- predict(fitR)
  predH2O <- predict(fitH2O, dec.hex)
  Log.info("R Predictions:"); print(head(predR))
  Log.info("H2O Predictions:"); print(head(predH2O))
  checkSignedCols(as.data.frame(predH2O), predR[,1:k], tolerance = 2e-5)
  
  
}

doTest("PUBDEV-1398: Compare projections of R and H2O PCA", test.pubdev.1398)
