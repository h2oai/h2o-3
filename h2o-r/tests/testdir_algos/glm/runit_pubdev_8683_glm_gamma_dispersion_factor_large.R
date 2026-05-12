setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# In this test, we are going to generate real gamma dataset, run H2O GLM and R GLM to compare our dispersion factor estimates.
##

test_glm_gammas <- function() {
  if (requireNamespace("tweedie")) {
    num_rows <- 10000
    num_cols <- 5
    f1 <- random_dataset_real_only(num_rows, num_cols, seed=12345) # generate dataset containing the predictors.
    f1R <- as.data.frame(h2o.abs(f1))
    weights <- c(0.01, 0.02, 0.03, 0.04, 0.05, 0.1) # glm coefficients
    mu <- generate_mean(f1R, num_rows, num_cols, weights)
    dispersion_factor <- c(1, 1.5, 2, 2.5, 3, 4, 5, 8)   # dispersion factor range
    pow_factor <- 2 # tweedie with p=2 is gamma
    y <- "resp"      # response column
    x <- c("abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.")
    for (ind in c(1:length(dispersion_factor))) { # generate dataset with each variance power and dispersion factor
      trainF <- generate_dataset(f1R, num_rows, num_cols, pow_factor, dispersion_factor[ind], mu)
      print(paste("Compare H2O, R GLM model coefficients and standard error for var_power=", pow_factor, "link_power=",1-pow_factor, sep=" "))
      compareH2ORGLM(pow_factor, 1-pow_factor, x, y, trainF, as.data.frame(trainF), dispersion_factor[ind])
    }
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

compareH2ORGLM <-
  function(vpower,
           lpower,
           x,
           y,
           hdf,
           df,
           truedisp,
           tolerance = 2e-4) {
    print("Define formula for R")
    formula <- (df[, "resp"] ~ .)
    rmodel <- tryCatch({
        glm(
          formula = formula,
          data = df[, x],
          family = tweedie(var.power = vpower, link.power =
                             lpower),
          na.action = na.omit
        )
    }, error = function(e) NULL)
    h2omodel <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "gamma",
        lambda = 0,
        nfolds = 0,
        compute_p_values = TRUE
      )
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
    print("Comparing H2O and R GLM model coefficients....")
    compareCoeffs(rmodel, h2omodel, tolerance, x)
    print("Comparing H2O and R GLM model dispersion estimations.  H2O performs better ....")
    print(paste("True dispersion factor", truedisp, sep = ":"))
    print(paste(
      "H2O model dispersion estimate using ML",
      h2omodel@model$dispersion,
      sep = ":"
    ))
    if (!is.null(rmodel)) {
        print(paste(
          "R model dispersion estimate",
          summary(rmodel)$dispersion,
          sep = ":"
        ))
    }
    h2oDiff = abs(h2omodel@model$dispersion - truedisp)
    if (is.null(rmodel)) {
        cat("R model did not converge!\n",
            "H2O dispersion estimate  - truedisp = ", h2oDiff, sep="")
        return()
    }
    rDiff = abs(summary(rmodel)$dispersion - truedisp)
    if (rDiff < h2oDiff) {
      val = (h2oDiff - rDiff)/truedisp
      expect_true(val < 0.05, info = "H2O performance degradation is too high.")
    } else {
      expect_true(
        h2oDiff <= rDiff,
        info = paste(
          "H2O dispersion estimation error",
          h2oDiff,
          "R dispersion estimation error",
          rDiff,
          sep = ":"
        )
      )
    }
  }

compareCoeffs <- function(rmodel, h2omodel, tolerance, x) {
  print("H2O GLM model....")
  print(h2omodel)
  if (is.null(rmodel)) {
    print("R model did not converge")
    return()
  }
  print("R GLM model....")
  print(summary(rmodel))
  h2oCoeff <- h2omodel@model$coefficients
  rCoeff <- coef(rmodel)
  for (ind in c(1:length(x))) {
    expect_true(abs(h2oCoeff[x[ind]]-rCoeff[x[ind]]) < tolerance, info = paste0(
      "R coefficient: ",
      rCoeff[x[ind]],
      " but h2o Coefficient: ",
      h2oCoeff[x[ind]],
      sep = " "
    ))
  }
  expect_true(abs(h2oCoeff[x[ind]]-rCoeff[x[ind]]) < tolerance, info = paste0(
    "R coefficient: ",
    rCoeff["(Intercept)"],
    " but h2o Coefficient: ",
    h2oCoeff["(Intercept)"],
    sep = " "
  ))
}

doTest("Comparison of H2O to R Gamma family coefficients and disperson with gamma dataset", test_glm_gammas)
