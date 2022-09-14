setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test_glm_tweedie_dispersion_parameter <- function() {
  train <-
    h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/tweedie_p5_phi0p5_10KRows.csv")
  x <- c("abs.C1.","abs.C2.","abs.C3.","abs.C4.","abs.C5.")
  y <- 'x'
  # use maximum likelihood to estimate dispersion parameter
  h2omodelML <-
    h2o.glm(
      x = x,
      y = y,
      training_frame = train,
      family = "tweedie",
      lambda = 0,
      nfolds = 0,
      dispersion_parameter_method = "ml",
      compute_p_values = TRUE,
      tweedie_variance_power = 3,
      init_dispersion_parameter = 0.1
    )
  # use maximum likelihood with higher initial guess
  h2omodelML2 <-
    h2o.glm(
      x = x,
      y = y,
      training_frame = train,
      family = "tweedie",
      lambda = 0,
      nfolds = 0,
      dispersion_parameter_method = "ml",
      init_dispersion_parameter = 1.5,
      tweedie_variance_power = 3,
      compute_p_values = TRUE
    )
  # use pearson to estimate dispersion parameter
  h2omodelP <-
    h2o.glm(
      x = x,
      y = y,
      training_frame = train,
      family = "tweedie",
      lambda = 0,
      nfolds = 0,
      compute_p_values = TRUE,
      tweedie_variance_power = 3
    )
  trueDispersion <- 0.5
  dispersionFactor <- h2omodelML@model$dispersion
  dispersionFactor2 <- h2omodelML2@model$dispersion
  dispersionFactorP <- h2omodelP@model$dispersion
  # ml dispersion estimate should be better than pearson
  expect_true(abs(dispersionFactor - trueDispersion) < abs(trueDispersion -
                                                             dispersionFactorP))
  expect_true(abs(dispersionFactor2 - trueDispersion) < abs(trueDispersion -
                                                              dispersionFactorP))
}

doTest("GLM tweedie, check ml dispersion parameter works.", test_glm_tweedie_dispersion_parameter)
