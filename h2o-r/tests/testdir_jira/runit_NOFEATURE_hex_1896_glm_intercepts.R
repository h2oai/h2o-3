## This test is to check the no-intercept argument for GLM
## The test will import the prostate data set,
## runs glm with and without intecept and create predictions from both models,
## compare the two h2o glm models with a glmnet model ran without offset.

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.GLM.zero_intercept <- function(conn) {
  Log.info("Importing prostate.csv data...\n")
  # Fix this next line!!
  prostate.hex = h2o.importFile(normalizePath(locate('smalldata/logreg/prostate.csv')))
  ## Rebalance should happen internally now
  # prostate.rebalanced = h2o.rebalance(data = prostate.hex, chunks = 16, key = "prostate.rebalanced")
  ## Import data into R
  prostate.R = as.data.frame(prostate.hex)

  ## Set paramters
  myY = "CAPSULE"
  myX = c("AGE","RACE","PSA","DCAPS")
  var_family = "binomial"
  var_folds = 0
  var_alpha = 1

  Log.info("Build binomial GLMnet model with and without intercept...")
  library(glmnet)
  r_Y = as.matrix(prostate.R[,myY])
  r_X = as.matrix(prostate.R[,myX])
  prostate.glmnet1 = glmnet(y = r_Y, x = r_X, family = var_family, alpha = var_alpha, intercept = TRUE)
  prostate.glmnet2 = glmnet(y = r_Y, x = r_X, family = var_family, alpha = var_alpha, intercept = FALSE)

  var_lambda1 = tail(prostate.glmnet1$lambda, n = 1)
  var_lambda2 = tail(prostate.glmnet2$lambda, n = 1)

  # Compare coefficients function
  check_coeff <- function(coeff.h2o, coeff.r, threshold = 0.1){
    coeff.h2o = coeff.h2o[names(coeff.r)]
    diff = abs(coeff.h2o - coeff.r)
    for (single_diff in diff) {
      if(!(single_diff < threshold)) stop('Difference in coefficient not within threshold.')
    }
    Log.info(paste('Coefficient difference within accepted threshold level of ',threshold))
  }

  Log.info("Build logistic model in H2O with intercept...")
  prostate.glm.h2o1 = h2o.glm(y = myY, x = myX, training_frame = prostate.hex, lambda = var_lambda1,
                             family = var_family, nfolds = var_folds, alpha = var_alpha, intercept = TRUE)
  Log.info("Build logistic model in H2O without intercept...")
  ## standardization must be set to false since there are no intercepts, we cannnot regularize
  prostate.glm.h2o2 = h2o.glm(y = myY, x = myX, training_frame = prostate.hex, lambda = var_lambda2, standardize = F,
                                family = var_family, nfolds = var_folds, alpha = var_alpha, intercept = FALSE)
  Log.info("Build logistic model in H2O w/o intercept w/ rebalanced data...")
  prostate.glm.h2o3 = h2o.glm(y = myY, x = myX, training_frame = prostate.rebalanced, lambda = var_lambda2, standardize = F,
                              family = var_family, nfolds = var_folds, alpha = var_alpha, intercept = FALSE)

  check_coeff(prostate.glm.h2o2@model$coefficients, prostate.glm.h2o3@model$coefficients, 1e-10)
  Log.info("Rebalanced data ran with same results.")

  # Force 0 intercept in coefficient list
  prostate.glm.h2o2@model$coefficients[length(myX)+1] = c(0)
  prostate.glm.h2o2@model$normalized_coefficients[length(myX)+1] = c(0)
  names(prostate.glm.h2o2@model$coefficients)[length(myX)+1] = "Intercept"
  names(prostate.glm.h2o2@model$normalized_coefficients)[length(myX)+1] = "Intercept"

  # Function for extracting glmnet coefficients
  glmnet_coeff <- function(myGLM.r) {
    coeff.mat = as.matrix(myGLM.r$beta)
    numcol = ncol(coeff.mat)
    coeff.R = c(coeff.mat[,numcol], Intercept = as.numeric(myGLM.r$a0[numcol]))
    return(coeff.R)
  }

  check_coeff(prostate.glm.h2o1@model$coefficients, glmnet_coeff(prostate.glmnet1))
  check_coeff(prostate.glm.h2o2@model$coefficients, glmnet_coeff(prostate.glmnet2))

  testEnd()
}

doTest("GLM Test: Intercepts", test.GLM.zero_intercept)
