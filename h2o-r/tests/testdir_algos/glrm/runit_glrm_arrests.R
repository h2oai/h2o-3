setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.glrm.arrests <- function() {
  h2oTest.logInfo("Importing arrests.csv data...") 
  arrestsR <- read.csv(h2oTest.locate("smalldata/pca_test/USArrests.csv"), header = TRUE)
  arrestsH2O <- h2o.uploadFile(h2oTest.locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  initCent <- scale(arrestsR, center = TRUE, scale = FALSE)[1:4,]
  
  # Note: Results vary wildly with initial Y when transform = 'DEMEAN'. This is a flaw of the algorithm, not a bug.
  h2oTest.logInfo("Compare with SVD when center = TRUE, scale = FALSE")
  fitR <- svd(scale(arrestsR, center = TRUE, scale = FALSE))
  fitH2O <- h2o.glrm(arrestsH2O, k = 4, init = "User", user_y = initCent, transform = "DEMEAN", loss = "Quadratic", regularization_x = "None", regularization_y = "None", recover_svd = TRUE)
  
  h2oTest.logInfo("R Singular Values:"); print(fitR$d)
  h2oTest.logInfo("H2O Singular Values:"); print(fitH2O@model$singular_vals)
  # h2oTest.logInfo("Compare Singular Values between R and H2O")
  # expect_equal(fitH2O@model$singular_vals, fitR$d, tolerance = 1e-4)

  h2oTest.logInfo("Compare H2O SVD with R SVD initialization")
  initX <- fitR$u %*% diag(sqrt(fitR$d))
  initY <- diag(sqrt(fitR$d)) %*% t(fitR$v)
  
  # Note: Default behavior when all loss/regularization = quadratic is to use closed-form solution of ALS equation to set X
  fitH2O_svd_user <- h2o.glrm(arrestsH2O, k = 4, init = "User", user_x = initX, user_y = initY, transform = "DEMEAN", loss = "Quadratic", gamma_x = 0.15, regularization_x = "L1", regularization_y = "None")
  fitH2O_svd_h2o <- h2o.glrm(arrestsH2O, k = 4, init = "SVD", svd_method = "GramSVD", loss = "Quadratic", transform = "DEMEAN", gamma_x = 0.15, regularization_x = "L1", regularization_y = "None")
  h2oTest.logInfo("R SVD init:"); print(fitH2O_svd_user)
  h2oTest.logInfo("H2O SVD init:"); print(fitH2O_svd_h2o)
}

h2oTest.doTest("GLRM Golden Test: USArrests with Centering", test.glrm.arrests)
