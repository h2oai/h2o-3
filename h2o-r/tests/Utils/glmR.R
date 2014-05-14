checkGLMModel <- function(myGLM.h2o, myGLM.r) {
  coeff.mat = as.matrix(myGLM.r$beta)
  numcol = ncol(coeff.mat)
  coeff.R = c(coeff.mat[,numcol], Intercept = as.numeric(myGLM.r$a0[numcol]))
  print(myGLM.h2o@model$coefficients)
  print(coeff.R)
  checkEqualsNumeric(myGLM.h2o@model$coefficients, coeff.R, tolerance = 3.8)
  checkEqualsNumeric(myGLM.h2o@model$null.deviance, myGLM.r$nulldev, tolerance = 1.5)
}
