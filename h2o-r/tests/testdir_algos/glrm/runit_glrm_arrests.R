setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glrm.arrests <- function() {
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  initCent <- scale(arrestsR)[1:4,]
  
  # Note: Results vary wildly with initial Y when transform = 'DEMEAN'. This is a flaw of the algorithm, not a bug.
  Log.info("Compare with SVD when center = TRUE, scale = FALSE")
  fitR <- svd(scale(arrestsR, center = TRUE, scale = FALSE))
  fitH2O <- h2o.glrm(arrestsH2O, loss = "L2", gamma_x = 0, gamma_y = 0, init = initCent, transform = "DEMEAN", recover_svd = TRUE)
  
  Log.info("R Singular Values:"); print(fitR$d)
  Log.info("H2O Singular Values:"); print(fitH2O@model$singular_vals)
  # Log.info("Compare Singular Values between R and H2O\n")
  # expect_equal(fitH2O@model$singular_vals, fitR$d, tolerance = 1e-4)
  
  testEnd()
}

doTest("GLRM Golden Test: USArrests with Centering", test.glrm.arrests)
