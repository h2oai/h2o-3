setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# should throw an error when compute_p_value=FALSE
testGLMcomptuePValueFALSE <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	mFV <- h2o.glm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV, lambda=1.0)

	assertError(coefWPValues <- h2o.coef_with_p_values(mFV))
}

doTest("GLM: make sure error is generated when compute_p_values=FALSE", testGLMcomptuePValueFALSE)

