setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# In this test, I want to check and make sure that if user set fix_dispersion_parameter for gamma
# family, the dispersion parameter should not change.
##

test_glm_gamma_fix_dispersion_parameter <- function() {
  if (requireNamespace("tweedie")) {
    num_rows <- 18000
    num_cols <- 5
    f1 <- random_dataset_real_only(num_rows, num_cols, seed=12345) # generate dataset containing the predictors.
    f1R <- as.data.frame(h2o.abs(f1))
    weights <- c(0.01, 0.02, 0.03, 0.04, 0.05, 0.1) # glm coefficients
    mu <- generate_mean(f1R, num_rows, num_cols, weights)
    dispersion_factor <- c(2)   # dispersion factor range
    pow_factor <- 2 # tweedie with p=2 is gamma
    y <- "resp"      # response column
    x <- c("abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.")

    hdf <- generate_dataset(f1R, num_rows, num_cols, pow_factor, dispersion_factor[1], mu)
    fixedDispersion <- 1.5
    # use maximum likelihood to estimate dispersion parameter
    h2omodel <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "gamma",
        lambda = 0,
        nfolds = 0,
        dispersion_parameter_method = "ml",
        compute_p_values = TRUE
      )
    # fixed dispersion parameter still specify ml, should have no effect
    h2omodel2 <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "gamma",
        lambda = 0,
        nfolds = 0,
        dispersion_parameter_method = "ml",
        fix_dispersion_parameter = TRUE,
        init_dispersion_parameter = fixedDispersion,
        compute_p_values = TRUE
      )
    # fixed dispersion parameter, use default pearson, should have no effect
    h2omodel3 <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "gamma",
        lambda = 0,
        nfolds = 0,
        fix_dispersion_parameter = TRUE,
        init_dispersion_parameter = fixedDispersion,
        compute_p_values = TRUE
      )
    dispersionFactor <- h2omodel@model$dispersion
    dispersionFactor2 <- h2omodel2@model$dispersion
    dispersionFactor3 <- h2omodel3@model$dispersion
    expect_true(abs(dispersionFactor-dispersionFactor2) > 0.1)
    expect_true(dispersionFactor2==dispersionFactor3)
    expect_true(fixedDispersion==dispersionFactor2)
  } else {
    print("test_glm_gamma is skipped.  Need to install tweedie package.")
  }
}

generate_dataset<-function(f1R, numRows, numCols, pow, phi, mu) {
  resp <- tweedie::rtweedie(numRows, xi=pow, mu, phi, power=pow)
  f1h2o <- as.h2o(f1R)
  resph2o <- as.h2o(as.data.frame(resp))
  finalFrame <- h2o.cbind(f1h2o, resph2o)
  return(finalFrame)
}

generate_mean<-function(f1R, numRows, numCols, weights) {
  y <- c(1:numRows)
  for (rowIndex in c(1:numRows)) {
    tempResp = 0.0
    for (colIndex in c(1:numCols)) {
      tempResp = tempResp+weights[colIndex]*f1R[rowIndex, colIndex]
    }
    y[rowIndex] = tempResp
  }
  return(y)
}

doTest("GLM gamma, check fix_dispersion_parameter works.", test_glm_gamma_fix_dispersion_parameter)
