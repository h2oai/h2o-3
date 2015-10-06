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
  fitH2O <- h2o.glrm(arrestsH2O, k = 4, init = "User", user_y = initCent, transform = "DEMEAN", loss = "Quadratic", regularization_x = "None", regularization_y = "None", recover_svd = TRUE)
  
  Log.info("R Singular Values:"); print(fitR$d)
  Log.info("H2O Singular Values:"); print(fitH2O@model$singular_vals)
  # Log.info("Compare Singular Values between R and H2O\n")
  # expect_equal(fitH2O@model$singular_vals, fitR$d, tolerance = 1e-4)

  Log.info("Compare H2O SVD with R SVD initialization")
  initX <- fitR$u %*% diag(sqrt(fitR$d))
  initY <- diag(sqrt(fitR$d)) %*% t(fitR$v)
  # Note: Default behavior when all loss/regularization = quadratic is to use closed-form solution of ALS equation to set X
  fitH2O_svd_user <- h2o.glrm(arrestsH2O, k = 4, init = "User", user_x = initX, user_y = initY, transform = "DEMEAN", loss = "Quadratic", gamma_x = 0.15, regularization_x = "L1", regularization_y = "None")
  fitH2O_svd_h2o <- h2o.glrm(arrestsH2O, k = 4, init = "SVD", loss = "Quadratic", transform = "DEMEAN", gamma_x = 0.15, regularization_x = "L1", regularization_y = "None")

  Log.info("R SVD init:"); print(fitH2O_svd_user)
  Log.info("H2O SVD init:"); print(fitH2O_svd_h2o)
  expect_equal(fitH2O_svd_h2o@model$step_size, fitH2O_svd_user@model$step_size)
  expect_equal(fitH2O_svd_h2o@model$objective, fitH2O_svd_user@model$objective)
  pred_svd_user <- predict(fitH2O_svd_user, arrestsH2O)
  pred_svd_h2o <- predict(fitH2O_svd_h2o, arrestsH2O)
  expect_equal(as.matrix(pred_svd_h2o), as.matrix(pred_svd_user))
}

doTest("GLRM Golden Test: USArrests with Centering", test.glrm.arrests)
