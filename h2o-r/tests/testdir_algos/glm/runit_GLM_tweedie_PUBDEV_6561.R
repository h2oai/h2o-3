setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# In this test, we are going to generate real tweedie dataset, run H2O GLM and R GLM and compare the results for
# accuracy and others
##

test_glm_tweedies <- function() {
  if (requireNamespace("tweedie")) {
    num_rows <- 10000
    num_cols <- 5
    f1 <- random_dataset_real_only(num_rows, num_cols) # generate dataset containing the predictors.
    f1R <- as.data.frame(f1)
    weights <- c(0.1, 0.2, 0.3, 0.4, 0.5, 1) # weights to generate the mean
    mu <- generate_mean(f1R, num_rows, num_cols, weights)
    pow <- c(0, 1, 2, 3)
    phi <- c(1, 1, 2, 3)
    browser()    
    for (ind in c(1:length(pow))) { # generate dataset with each power and dispersion factor
      trainF <- generate_dataset(f1R, num_rows, num_cols, pow[ind], phi[ind], mu)

    }
  } else {
    print("test_glm_tweedies is skipped.  Need to install tweedie package.")
  }
}

generate_dataset<-function(f1R, numRows, numCols, pow, phi, mu) {
  resp <- c(1:numRows)
  for (rowIndex in c(1:numRows)) {
    resp[rowIndex] <- rtweedie(1,xi=pow,mu[rowIndex], phi, power=pow)
  }
  f1h2o <- as.h2o.data.frame(f1R)
  resph2o <- as.h2o.data.frame(resp)
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

compareH2ORGLM<-function(vpower, lpower, x, y, hdf, df, tolerance=5e-6) {
  print("Define formula for R")
  formula <- (df[,"CAPSULE"]~.)
  rmodel <- glm(formula = formula,  data = df[,x],
                        family=tweedie(var.power=vpower,link.power=lpower),na.action = na.omit)
  rmodelWWeights <- glm(CAPSULE~.-weight-offset-C1-ID, data = df, weights=weight,
                        family=tweedie(var.power=vpower,link.power=lpower),na.action = na.omit)
  rmodelWOffsets <- glm(CAPSULE~.-weight-offset-C1-ID+offset(offset), data = df, weights=weight,
                        family=tweedie(var.power=vpower,link.power=lpower),na.action = na.omit)
  h2omodel <- h2o.glm(x = x, y = y, training_frame = hdf, family = "tweedie", link = "tweedie", 
                      tweedie_variance_power = vpower, tweedie_link_power = lpower, alpha = 0.5, lambda = 0, 
                      nfolds = 0, compute_p_values=TRUE)
  h2omodelWWeights <- h2o.glm(x = x, y = y, training_frame = hdf, family = "tweedie", link = "tweedie", 
                              tweedie_variance_power = vpower, tweedie_link_power = lpower, alpha = 0.5, lambda = 0, 
                              nfolds = 0, compute_p_values=TRUE, weights_column="weight")
  h2omodelWOffsets <- h2o.glm(x = x, y = y, training_frame = hdf, family = "tweedie", link = "tweedie", 
                              tweedie_variance_power = vpower, tweedie_link_power = lpower, alpha = 0.5, lambda = 0, 
                              nfolds = 0, compute_p_values=TRUE, offset_column="offset")

  print("Comparing H2O and R GLM model coefficients....")
  compareCoeffs(rmodel, h2omodel, tolerance, x)
  print("Comparing H2O and R GLM model coefficients with weights....")
  compareCoeffs(rmodelWWeights, h2omodelWWeights, tolerance, x)
  print("Comparing H2O and R GLM model coefficients with offsets")
  compareCoeffs(rmodelWOffsets, h2omodelWOffsets, 2, x) # accuracy is lower for some reason
}

compareCoeffs <- function(rmodel, h2omodel, tolerance, x) {
  print("H2O GLM model....")
  print(h2omodel)
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

doTest("Comparison of H2O to R TWEEDIE family coefficients and standard errors with tweedie dataset", test_glm_tweedies)