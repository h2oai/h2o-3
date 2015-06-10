setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1383 <- function(conn) {
  k <- 10
  Log.info("Importing fgl_tr.csv...")
  fgl.dat <- read.csv(locate("smalldata/pca_test/fgl_tr.csv"))
  fgl.hex <- h2o.importFile(conn, locate("smalldata/pca_test/fgl_tr.csv"))
  print(summary(fgl.hex))
  
  Log.info("Reshuffling R data to match H2O...")
  isNum <- sapply(fgl.dat, is.numeric)
  fgl.mm <- fgl.dat
  fgl.mm[,isNum] <- scale(fgl.mm[,isNum], center = TRUE, scale = TRUE)   # Standardize numeric columns
  fgl.mm <- fgl.mm[,c(which(!isNum), which(isNum))]   # Move categorical column to front
  fgl.mm <- model.matrix(~ . -1, fgl.mm)
  fgl.mm <- fgl.mm[,-1]
  print(summary(fgl.mm))
  
  Log.info("Building PCA model...")
  fitR <- prcomp(fgl.mm, center = FALSE, scale. = FALSE)
  fitH2O <- h2o.prcomp(fgl.hex, k = k, transform = "STANDARDIZE", max_iterations = 5000, use_all_factor_levels = FALSE)
  
  Log.info("R Eigenvectors:"); print(fitR$rotation[,1:k])
  Log.info("H2O Eigenvectors:"); print(fitH2O@model$eigenvectors)
  checkSignedCols(fitH2O@model$eigenvectors, fitR$rotation[,1:k], tolerance = 1e-5)
  
  impR <- summary(fitR)$importance
  impH2O <- fitH2O@model$pc_importance
  Log.info("R PC Importance:"); print(impR[,1:k])
  Log.info("H2O PC Importance:"); print(impH2O)
  expect_equal(as.numeric(impR[1,1:k]), as.numeric(impH2O[1,]), tolerance = 1e-6)
  
  testEnd()
}

doTest("PUBDEV-1383: Compare numerical accuracy of H2O and R PCA", test.pubdev.1383)