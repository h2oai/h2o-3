setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testGLMVIF <- function() {
	bhexFV <- h2o.importFile(locate("smalldata/logreg/benign.csv"))
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]
	bhexFV$FNDX <- h2o.asfactor(bhexFV$FNDX)
	mFV <- h2o.glm(y=Y, x=colnames(bhexFV)[X], training_frame=bhexFV, family="binomial", lambda=0.0, compute_p_values=TRUE,
	               generate_variable_inflation_factors=TRUE)
  glmVIF <- h2o.get_variable_inflation_factors(mFV) # get variable inflation factors
  glmCoefName <- mFV@model$coefficients_table$names
  glmCoefVal <- mFV@model$coefficients_table$variable_inflation_factor
  for (ind in c(1:8)) {
    expect_true(abs(glmVIF[[ind]]-glmCoefVal[ind+1])<1e-6)
  }
}

doTest("GLM: test variable inflation factors", testGLMVIF)

