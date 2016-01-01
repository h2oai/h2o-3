setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
## This test is to check the beta contraint argument for GLM
## The test will import the prostate data set,
## runs glm with and without beta contraints which will be checked
## against glmnet's results.




test.LR.betaConstraints <- function(){

  #h2oTest.logInfo("Importing prostate dataset...")
  prostate_h2o <- h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv"))

  #h2oTest.logInfo("Create beta constraints frame...")
  myX <-  c("AGE","RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON")
  lowerbound <- rep(-1, times = length(myX))
  upperbound <- rep(1, times = length(myX))
  betaConstraints <- data.frame(names = myX, lower_bounds = lowerbound, upper_bounds = upperbound)
  prostate_r <- as.data.frame(prostate_h2o)

  ######## Single variable CAPSULE ~ AGE in H2O and then R
  ## actual coeff for Age without constraints = -.00823
  #h2oTest.logInfo("Run a Linear Regression with CAPSULE ~ AGE with bound beta->[0,1] in H2O...")
  beta_age <- betaConstraints[betaConstraints$names == "AGE",]
  beta_age$lower_bounds <- 0
  beta_age$upper_bounds <- 1
  lr.h2o <- h2o.glm(x = "AGE", y = "CAPSULE", training_frame = prostate_h2o, family = "gaussian", alpha = 0,
                    beta_constraints = beta_age)
  lambda <- lr.h2o@allparameters$lambda

  #h2oTest.logInfo("Run a Linear Regression with CAPSULE ~ AGE with bound beta->[0,1] in R...")
  intercept <- rep(0, times = nrow(prostate_h2o))
  xDataH2OFrame <- data.frame(AGE = prostate_r[,"AGE"], Intercept = intercept)
  xMatrix_age <- as.matrix(xDataH2OFrame)
  lr.R <- glmnet(x = xMatrix_age, alpha = 0., lambda = lr.h2o@model$lambda, standardize = T,
                 y = prostate_r[,"CAPSULE"], family = "gaussian", lower.limits = 0, upper.limits = 1)
  h2oTest.checkGLMModel2(lr.h2o, lr.R)

  #### shift AGE coefficient by 0.002
  run_glm <- function(family_type) {
    #h2oTest.logInfo("Test Beta Constraints with negative upper bound in H2O...")
    beta_age$lower_bounds <- -0.008
    beta_age$upper_bounds <- -0.002
    nrow_prior <- nrow(prostate_h2o)
    lr_negativeUpper.h2o <- h2o.glm(x = "AGE", y = "CAPSULE", training_frame = prostate_h2o, family = family_type,
                                    alpha = 0, beta_constraints = beta_age)
    nrow_after <- nrow(prostate_h2o)
    if(!nrow_prior == nrow_after) stop("H2OParsedData object is being overwritten.")

    #h2oTest.logInfo("Shift AGE column to reflect negative upperbound...")
    xDataH2OFrame <- data.frame(AGE = prostate_r[,"AGE"]*(1+-0.002), Intercept = intercept)
    xMatrix_age <- as.matrix(xDataH2OFrame)
    lr_negativeUpper.R <- glmnet(x = xMatrix_age, alpha = 0., lambda = lr.h2o@model$lambda, standardize = T,
                                 y = prostate_r[,"CAPSULE"], family = family_type, lower.limits = -0.008,
                                 upper.limits = 0.0)
    h2oTest.checkGLMModel2(lr_negativeUpper.h2o, lr_negativeUpper.R)
  }

  full_test <- sapply(c("binomial", "gaussian"), run_glm)
  print(full_test)
  
}

h2oTest.doTest("GLM Test: LR w/ Beta Constraints", test.LR.betaConstraints)

