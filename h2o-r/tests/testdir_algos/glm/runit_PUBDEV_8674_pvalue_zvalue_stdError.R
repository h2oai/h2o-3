setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testGLMPValZValStdError <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	bhexFV$FNDX <- h2o.asfactor(bhexFV$FNDX)
	mFV <- h2o.glm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV, family="binomial", lambda=0.0, compute_p_values=TRUE)

	coefPValues <- h2o.coef_with_p_values(mFV)
	print("coefficients table with p-values, z-values and std-error")
	print(coefPValues)
	coefPValue <- coefPValues$p_value
	coefZValue <- coefPValues$z_value
	coefStdErr <- coefPValues$std_error
	
	manuelPValues <- mFV@model$coefficients_table$p_value
	manuelZValues <- mFV@model$coefficients_table$z_value
	manuelStdError <- mFV@model$coefficients_table$std_error
	
	# compare values from model and obtained manually
  for (ind in c(1:length(manuelPValues)))
    expect_equal(manuelPValues[ind], coefPValue[ind])
	  expect_equal(manuelZValues[ind], coefZValue[ind])
	  expect_equal(manuelStdError[ind], coefStdErr[ind])
}

doTest("GLM: test p-values, z-values, std-error", testGLMPValZValStdError)

