setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# call glm functions by a gbm model.  Should generate an error
testGBMvcov <- function() {
	cars <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
	y = "economy_20mpg"
	predictors = c("displacement","power","weight","acceleration","year")
	cars[y] = h2o.asfactor(cars[y])
	mFV <- h2o.gbm(y=y, x=predictors, training_frame=cars)

	assertError(vcovValues <- h2o.vcov(mFV))
}

# should throw an error when compute_p_value=FALSE
testGLMvcovcomputePValueFALSE <- function() {
	cars <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
	y = "economy_20mpg"
	predictors = c("displacement","power","weight","acceleration","year")
	cars[y] = h2o.asfactor(cars[y])
	mFV <- h2o.glm(y=y, x=predictors, training_frame=cars, lambda=1.0, compute_p_values = FALSE)

	assertError(vcovValues <- h2o.vcov(mFV))
}

testGLMvcovValues <- function() {
	cars <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
	y = "economy_20mpg"
	predictors = c("displacement","power","weight","acceleration","year")
	cars[y] = h2o.asfactor(cars[y])
	mFV <- h2o.glm(y=y, x=predictors, training_frame=cars, family="binomial", lambda=0.0, compute_p_values=TRUE)

	vcovValues <- h2o.vcov(mFV)
	print("variance-covariance table")
	print(vcovValues)
	vcovIntercept <- vcovValues$Intercept
	vcovDisplacement <- vcovValues$displacement
	vcovPower <- vcovValues$power
	vcovWeight <- vcovValues$weight
	vcovAcceleration <- vcovValues$acceleration
	vcovYear <- vcovValues$year
	
	hf_vcov <- h2o.getFrame(mFV@model$vcov_table$name)
	manualIntercept <- hf_vcov$Intercept
	manualDisplacement <- hf_vcov$displacement
	manualPower <- hf_vcov$power
	manualWeight <- hf_vcov$weight
	manualAcceleration <- hf_vcov$acceleration
	manualYear <- hf_vcov$year
	
  # compare values from function with those obtained manually
  for (i in seq_along(c("Intercept", predictors)))
    expect_equal(manualIntercept[i], vcovIntercept[i])
    expect_equal(manualDisplacement[i], vcovDisplacement[i])
    expect_equal(manualPower[i], vcovPower[i])
    expect_equal(manualWeight[i], vcovWeight[i])
    expect_equal(manualAcceleration[i], vcovAcceleration[i])
    expect_equal(manualYear[i], vcovYear[i])
}

doSuite("GLM: VCOV support",
    makeSuite(
	testGBMvcov, 
	testGLMvcovcomputePValueFALSE, 
	testGLMvcovValues
	))