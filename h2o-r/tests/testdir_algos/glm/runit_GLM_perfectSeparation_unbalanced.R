setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Testing glm performance (reasonable coefficients) on unbalanced synthetic dataset with perfect separation.
# Separation recognized by R glm with following warning: 
#       1: glm.fit: algorithm did not converge 
#       2: glm.fit: fitted probabilities numerically 0 or 1 occurred 
##






test <- function() {

    print("Read in synthetic unbalanced dataset")
        data.u.hex <- h2o.uploadFile(h2oTest.locate("smalldata/synthetic_perfect_separation/unbalanced.csv"), destination_frame="data.u.hex")

    print("Fit model on dataset.")
        model.unbalanced <- h2o.glm(x=c("x1", "x2"), y="y", data.u.hex, family="binomial", alpha=0, nfolds=0, lambda=1e-8)

    print("Extract models' coefficients and assert reasonable values (ie. no greater than 50)")
    print("Unbalanced dataset")
            coef <- model.unbalanced@model$coefficients
        suppressWarnings((coef$"Intercept"<-NULL))
        stopifnot(coef < 50)

    
}

h2oTest.doTest("Testing glm performance on unbalanced synthetic dataset with perfect separation.", test)
