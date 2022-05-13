setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# call glm functions by a gbm model.  Should generate an error
testGBMpValueZValueStdErr <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	mFV <- h2o.gbm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV)

	assertError(coefWPValues <- h2o.coef_with_p_values(mFV))
}

doTest("GLM: make sure error is generated when a gbm model calls glm functions", testGBMpValueZValueStdErr)

