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
    f1R <- as.data.frame(h2o.abs(f1))
    weights <- c(0.1, 0.2, 0.3, 0.4, 0.5, 1) # weights to generate the mean
    mu <- generate_mean(f1R, num_rows, num_cols, weights)
    pow <- c(1.1, 1.1, 1.1, 1.5, 1.5, 1.5, 1.9, 1.9, 1.9, 2.1, 2.1, 2.1, 2.5, 2.5, 2.5, 10, 10, 10) # variance power range
    phi <- c(0.1, 1, 10, 0.1, 1, 10, 0.1, 1, 10, 0.1, 1, 10, 0.1, 1, 10, 0.1, 1, 10)   # dispersion factor range
    y <- "resp"      # response column
    x <- c("abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.")
    for (ind in c(1:length(pow))) { # generate dataset with each variance power and dispersion factor
      trainF <- generate_dataset(f1R, num_rows, num_cols, pow[ind], phi[ind], mu)
      checkOutH2O(pow[ind], 1-pow[ind], x, y, trainF, as.data.frame(trainF), phi[ind])
    }
  } else {
    print("test_glm_tweedies is skipped.  Need to install tweedie package.")
  }
}

generate_dataset<-function(f1R, numRows, numCols, pow, phi, mu) {
  # resp <- c(1:numRows)
  # for (rowIndex in c(1:numRows)) {
  #   resp[rowIndex] <- tweedie::rtweedie(1,xi=pow,mu[rowIndex], phi, power=pow)
  # }
  resp <- tweedie::rtweedie(numRows, xi=pow, mu, phi, power=pow)
  f1h2o <- as.h2o.data.frame(f1R)
  resph2o <- as.h2o.data.frame(as.data.frame(resp))
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

checkOutH2O <-
  function(vpower,
           lpower,
           x,
           y,
           hdf,
           df,
           truedisp,
           tolerance = 2e-4) {
    print("Fixing variance power and just estimate dispersion parameters")
    h2omodel1 <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "tweedie",
        link = "tweedie",
        tweedie_variance_power = vpower,
        tweedie_link_power = lpower,
        alpha = 0.5,
        lambda = 0,
        nfolds = 0,
        dispersion_parameter_method="ml",
        fix_dispersion_parameter=FALSE,
        fix_tweedie_variance_power=TRUE,
        compute_p_values = TRUE
      )
    printDispersion(h2omodel1, vpower, truedisp)
    print("Fixing dispersion parameters and just estimate variance power")
    h2omodel2 <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "tweedie",
        link = "tweedie",
        tweedie_variance_power = vpower,
        tweedie_link_power = lpower,
        alpha = 0.5,
        lambda = 0,
        nfolds = 0,
        init_dispersion_parameter = truedisp,
        dispersion_parameter_method="ml",
        fix_dispersion_parameter=TRUE,
        fix_tweedie_variance_power=FALSE,
        compute_p_values = TRUE
      )
    printVpower(h2oModel2, vpower, trueDisp)
    print("Estimate both dispersion and variance power")
    h2omodel3 <-
      h2o.glm(
        x = x,
        y = y,
        training_frame = hdf,
        family = "tweedie",
        link = "tweedie",
        tweedie_variance_power = vpower,
        tweedie_link_power = lpower,
        alpha = 0.5,
        lambda = 0,
        nfolds = 0,
        init_dispersion_parameter = truedisp,
        dispersion_parameter_method="ml",
        fix_dispersion_parameter=TRUE,
        fix_tweedie_variance_power=TRUE,
        compute_p_values = TRUE
      )
    printDispersion(h2oModel3, vpower, trueDisp)
    printVpower(h2oModel3, vpower, trueDisp)
  }

printDispersion <- function(h2oModel, vPower, trueDisp) {
  print(past("true variance power ", vPower))
  print(paste("true dispersion parameter ", truedisp))
  print(paste(
    "H2O model dispersion estimate",
    h2oModel@parameters$init_dispersion_parameter,
    sep = ":"
  ))
}

printVpower <- function(h2oModel, vPower, trueDisp) {
  print(past("true dispersion power ", trueDisp))
  print(paste("true variance power ", vpower))
  print(paste("H2O model variance power estimation", h2oModel@parameters$tweedie_variance_power))
}



doTest("Comparison of H2O to R TWEEDIE family coefficients and disperson with tweedie dataset", test_glm_tweedies)
