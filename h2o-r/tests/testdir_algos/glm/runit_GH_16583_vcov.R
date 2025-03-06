setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# call glm functions by a gbm model.  Should generate an error
testGBMvcov <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	mFV <- h2o.gbm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV)

	assertError(vcovValues <- h2o.vcov(mFV))
}

# should throw an error when compute_p_value=FALSE
testGLMvcovcomputePValueFALSE <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	mFV <- h2o.glm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV, lambda=1.0)

	assertError(vcovValues <- h2o.vcov(mFV))
}

testGLMPValZValStdError <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	bhexFV$FNDX <- h2o.asfactor(bhexFV$FNDX)
	mFV <- h2o.glm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV, family="binomial", lambda=0.0, compute_p_values=TRUE)

	vcovValues <- h2o.coef_with_p_values(mFV)
	print("variance-covariance table")
	print(vcovValues)
	vcovIntercept <- vcovValues$intercept
	vcovDisplacement <- vcovValues$displacement
	vcovPower <- vcovValues$power
	vcovWeight <- vcovValues$weight
	vcovAcceleration <- vcovValues$acceleration
	vcovYear <- vcovValues$year
	
	manualIntercept <- mFV@model$coefficients_table$intercept
	manualDisplacement <- mFV@model$coefficients_table$displacement
	manualPower <- mFV@model$coefficients_table$power
	manualWeight <- mFV@model$coefficients_table$weight
	manualAcceleration <- mFV@model$coefficients_table$acceleration
	manualYear <- mFV@model$coefficients_table$year
	
  # compare values from model and obtained manually
  for (ind in c(1:length(manuelPValues)))
    expect_equal(manualIntercept[ind], vcovIntercept[ind])
    expect_equal(manualDisplacement[ind], vcovDisplacement[ind])
    expect_equal(manualPower[ind], vcovPower[ind])
    expect_equal(manualWeight[ind], vcovWeight[ind])
    expect_equal(manualAcceleration[ind], vcovAcceleration[ind])
    expect_equal(manualYear[ind], vcovYear[ind])
}

doTest("GLM: make sure error is generated when a gbm model calls glm functions", testGBMvcov)
doTest("GLM: make sure error is generated when compute_p_values=FALSE", testGLMvcovcomputePValueFALSE)
doTest("GLM: test variance-covariance values", testGLMPValZValStdError)
