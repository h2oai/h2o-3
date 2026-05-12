setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# In this test, we check and make sure we can build null GLM model with coefficients for the intercept only.
##

test_gamma_null_model <- function() {
  if (requireNamespace("tweedie")) {
    num_rows <- 8000
    num_cols <- 5
    f1 <- random_dataset_real_only(num_rows, num_cols, seed=12345) # generate dataset containing the predictors.
    f1R <- as.data.frame(h2o.abs(f1))
    weights <- c(0.01, 0.02, 0.03, 0.04, 0.05, 0.1) # glm coefficients
    mu <- generate_mean(f1R, num_rows, num_cols, weights)
    dispersion_factor <- c(1)   # dispersion factor range
    pow_factor <- 2 # tweedie with p=2 is gamma
    y <- "resp"      # response column
    x <- c("abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.")
    trainF <- generate_dataset(f1R, num_rows, num_cols, pow_factor, dispersion_factor[1], mu)
    h2oModel <- h2o.glm(x=x, y=y, training_frame=trainF, family="gamma", lambda=0, compute_p_values=TRUE)
    h2oModelNull <- h2o.glm(x=x, y=y, training_frame=trainF, family="gamma", lambda=0, compute_p_values=TRUE,
                            build_null_model=TRUE)
    coef <- h2o.coef(h2oModel)
    coef_null <- h2o.coef(h2oModelNull)
    expect_true(length(h2oModel@model$coefficients) > length(h2oModelNull@model$coefficients))
    expect_true(length(h2oModelNull@model$coefficients)==1)
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

doTest("Confirm null model contains only 1 coefficient", test_gamma_null_model)
