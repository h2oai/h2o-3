library(glmnet)
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# I am testing when we have an alpha array and no lambda, GLM should still build and report back submodel parameters
# in RegularizationPath.  Simple test to make sure alphas are reported back in regularization path.
test.glm_reg_path <- function() {
    browser()
    d <-  h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))
    alphaArray <- c(0.1,0.5,0.9)
    m = h2o.glm(training_frame=d,x=3:9,y=2,family='binomial',alpha=alphaArray)
    regpath = h2o.getGLMFullRegularizationPath(m)
  
    expect_true(length(alphaArray)==length(regpath$alphas))
}

doTest("GLM Regularization Path extraction with alpha array but no lambda specified", test.glm_reg_path)