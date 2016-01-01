h2oTest.checkGLMModel <- function(myGLM.h2o, myGLM.r) {
  coeff.mat = as.matrix(myGLM.r$beta)
  numcol = ncol(coeff.mat)
  coeff.R = c(coeff.mat[,numcol], Intercept = as.numeric(myGLM.r$a0[numcol]))
  print("H2O Coefficients")
  print(myGLM.h2o@model$coefficients_table$coefficients)
  print("R Coefficients")
  print(coeff.R)

  print("SORTED COEFFS")
  print("H2O Coefficients")
  print(sort(myGLM.h2o@model$coefficients_table$coefficients))
  print("R Coefficients")
  print(sort(coeff.R))
  checkEqualsNumeric(sort(h2o.coef(myGLM.h2o)), sort(coeff.R), tolerance = 3.8)
  checkEqualsNumeric(h2o.null_deviance(myGLM.h2o), myGLM.r$nulldev, tolerance = 1.5)
}

l1norm <- function(x) sum(abs(x))
l2norm <- function(x) sum(x^2)
penalty <- function(alpha, beta){
  (1-alpha) * l2norm(beta) + alpha * l1norm(beta)
}

h2oTest.gaussianObj <- function(deviance, nobs, lambda, alpha, beta) {
  deviance * (1/(2*nobs)) + lambda * penalty(alpha, beta)
}

h2oTest.binomialObj <- function(deviance, nobs, lambda, alpha, beta) {
  deviance * (1/(2*nobs)) + lambda * penalty(alpha, beta)
}

h2oTest.checkGLMModel2 <- function(myGLM.h2o,myGLM.r){
  if(inherits(myGLM.h2o, "H2OModel")){
    f = myGLM.h2o@allparameters$family
    dev = myGLM.h2o@model$training_metrics@metrics$null_deviance
    nobs = myGLM.h2o@model$training_metrics@metrics$residual_degrees_of_freedom
    lambda = myGLM.h2o@allparameters$lambda
    alpha = myGLM.h2o@allparameters$alpha
    numfeat = length(myGLM.h2o@model$coefficients)
    beta = myGLM.h2o@model$coefficients[-1]

    r_lambda = myGLM.r$lambda
    r_dev = myGLM.r$nulldev*(1-myGLM.r$dev.ratio[length(r_lambda)])
    r_nobs = myGLM.r$nobs
    r_beta = myGLM.r$beta[-1,length(r_lambda)]
  }

  if(f == "gaussian"){
    res_h2o = h2oTest.gaussianObj(dev, nobs, lambda, alpha, beta)
    res_r = h2oTest.gaussianObj(r_dev, nobs, lambda, alpha, r_beta)
  } else {
    res_h2o = h2oTest.binomialObj(dev, nobs, lambda, alpha, beta)
    res_r = h2oTest.binomialObj(r_dev, nobs, lambda, alpha, r_beta)
  }
  print(paste0("GLMNET OBJECTIVE VALUE : ", res_r))
  print(paste0("H2O OBJECTIVE VALUE : ", res_h2o))

  print(paste0("GLMNET RESIDUAL DEVIANCE : ", r_dev))
  print(paste0("H2O RESIDUAL DEVIANCE : ", dev))

  print("SORTED COEFFS")
  print("GLMNET Coefficients")
  print(sort(r_beta))
  print("H2O Coefficients")
  print(sort(beta))

  # If objective value vary by more than 1% the test will fail.
  if(res_h2o < res_r) TRUE else checkEqualsNumeric(res_h2o , res_r, tolerance = .01)
}
