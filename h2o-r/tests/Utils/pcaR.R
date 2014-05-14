checkPCAModel <- function(myPCA.h2o, myPCA.r, toleq = 1e-5) {
  checkEqualsNumeric(myPCA.h2o@model$sdev, myPCA.r$sdev)
  ncomp = length(colnames(myPCA.h2o@model$rotation))
  myPCA.h2o@model$rotation = apply(myPCA.h2o@model$rotation, 2, as.numeric)
  flipped = abs(myPCA.h2o@model$rotation[1,] + myPCA.r$rotation[1,]) <= toleq
  for(i in 1:ncomp) {
    if(flipped[i])
      checkEqualsNumeric(myPCA.h2o@model$rotation[,i], -myPCA.r$rotation[,i])
    else
      checkEqualsNumeric(myPCA.h2o@model$rotation[,i], myPCA.r$rotation[,i])
  }
}
