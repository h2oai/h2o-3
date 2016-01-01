setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Testing glm performance (reasonable coefficients) on balanced synthetic dataset with perfect separation.
# Separation recognized by R glm with following warning: 
#       1: glm.fit: algorithm did not converge 
#       2: glm.fit: fitted probabilities numerically 0 or 1 occurred 
##






test <- function() {

    print("Read in synthetic balanced dataset")
        data.b.hex <- h2o.uploadFile(h2oTest.locate("smalldata/synthetic_perfect_separation/balanced.csv"), destination_frame="data.b.hex")

    print("Fit model on dataset.")
        model.balanced <- h2o.glm(x=c("x1", "x2"), y="y", data.b.hex, family="binomial", lambda_search=TRUE, alpha=0, nfolds=0, lambda=1e-8)

    print("Extract models' coefficients and assert reasonable values (ie. no greater than 50)")
    print("Balanced dataset")
    coef <- model.balanced@model$coefficients
    suppressWarnings((coef$"Intercept"<-NULL))
    stopifnot(coef < 50)

    
}

h2oTest.doTest("Testing glm performance on balanced synthetic dataset with perfect separation.", test)
