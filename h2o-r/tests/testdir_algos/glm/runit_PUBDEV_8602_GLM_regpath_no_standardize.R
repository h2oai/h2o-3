setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm_reg_path_no_standardize <- function() {
    # first run with no validation, compare against itself and glmnet
    d <-  h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))
    m = h2o.glm(training_frame=d,x=3:9,y=2,family='binomial',alpha=1,lambda_search = TRUE, solver='COORDINATE_DESCENT', standardize=FALSE)
    regpath = h2o.getGLMFullRegularizationPath(m)
    expect_true(is.null(regpath$coefficients_std))
}

doTest("GLM Regularization Path with on standardization", test.glm_reg_path_no_standardize)
