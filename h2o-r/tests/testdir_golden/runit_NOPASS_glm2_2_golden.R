setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.glm2silentreg.golden <- function(H2Oserver) {
	
#Import data: 
Log.info("Importing CUSE data...") 
	
cuseR<- read.csv(locate("smalldata/cuseexpanded.csv"), header=T)

Log.info("Test H2O treatment of Binomial Expanded Factors")
#Build design matrix
DM<- as.matrix(cbind(cuseR[, c(2, 3, 4, 5, 7, 8, 10, 11)]))
DV<- as.matrix(cuseR[,14])

#Fit a glmnet model with collinear cols. The model specified has SPECIFICALLY REQUESTED NO REGULARIZATION, and a non-spd matrix, thus a warning is returned
expect_warning(fitR<- glmnet(x=DM, y=DV, family="gaussian", alpha=0, nlambda=0, standardize=F, intercept=T))

#H2O should return a warning (similar to the behavior demostrated above) because we have specified nonspd matrix AND no regularization. Returning a model implies that we have regularized the coefficients, which is bad, because the user is getting a model other than the one they specified- the true specification is also not returned to the user. 
cuseH2O <- h2o.importFile(H2Oserver, locate("smalldata/cuseexpanded.csv"))
expect_warning(fitH2O<- h2o.glm(y="Percentuse", x=c("AgeA", "AgeB","AgeC", "AgeD", "LowEd", "HighEd",  "MoreYes", "MoreNo"), data=cuseH2O, family="gaussian", lambda=0, alpha=0, nfolds=0))

expect_true(fitH2O@model$params$lambda > 0)


testEnd()
}

doTest("GLM Test: Silent Regularization", test.glm2silentreg.golden)

