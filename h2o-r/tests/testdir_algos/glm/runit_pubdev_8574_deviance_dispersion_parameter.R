setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# In this test, I want to check to make sure deviance option works to estimate the dispersion parameter.
##

test_glm_deviance_dispersion_parameter <- function() {
  train <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
  y <- "resp"
  x <- c("abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.")
  h2omodel <- h2o.glm(x = x, y = y, training_frame = train, family = "gamma", lambda = 0, dispersion_parameter_method = "deviance", compute_p_values = TRUE)
  h2omodelML <- h2o.glm(x = x, y = y, training_frame = train, family = "gamma", lambda = 0, dispersion_parameter_method = "ml", compute_p_values = TRUE)
  
  dispersionFactor <- h2omodel@model$dispersion
  dispersionFactorML <-  h2omodelML@model$dispersion
  trueDispersion <- 9
  expect_true(abs(trueDispersion-dispersionFactorML) < abs(trueDispersion-dispersionFactor))
}

doTest("GLM gamma, check dispersion parameter estimation works with deviance option.", test_glm_deviance_dispersion_parameter)
