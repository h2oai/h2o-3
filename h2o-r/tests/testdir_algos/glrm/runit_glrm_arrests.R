setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glrmvanilla.golden <- function() {
  Log.info("Importing arrests.csv data...") 
  arrestsR <- read.csv(locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile( locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  initCent <- scale(arrestsR)[1:4,]
  
  Log.info("Compare with PCA when center = TRUE, scale. = TRUE")
  fitR <- prcomp(arrestsR, center = TRUE, scale. = TRUE)
  fitH2O <- h2o.glrm(arrestsH2O, k=4, init = "User",user_y=initCent, transform = "STANDARDIZE", loss="Quadratic", regularization_x="None",regularization_y="None",recover_svd = TRUE)
#   checkPCAModel(fitH2O, fitR, tolerance = 1e-4)
  
  pcimpR <- summary(fitR)$importance
  pcimpH2O <- fitH2O@model$pc_importance
  Log.info("R Importance of Components:"); print(pcimpR)
  Log.info("H2O Importance of Components:"); print(pcimpH2O)
  # Log.info("Compare Importance between R and H2O\n")
  # expect_equal(as.matrix(pcimpH2O), as.matrix(pcimpR), tolerance = 1e-4)
  
}

doTest("GLRM Golden Test: USArrests with Centering", test.glrmvanilla.golden)
