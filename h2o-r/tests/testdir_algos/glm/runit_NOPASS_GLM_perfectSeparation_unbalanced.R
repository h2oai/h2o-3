##
# Testing glm performance (reasonable coefficients) on unbalanced synthetic dataset with perfect separation.
# Separation recognized by R glm with following warning: 
#       1: glm.fit: algorithm did not converge 
#       2: glm.fit: fitted probabilities numerically 0 or 1 occurred 
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test <- function(conn) {

    print("Read in synthetic unbalanced dataset")
        data.u.hex <- h2o.uploadFile(conn, locate("smalldata/synthetic_perfect_separation/unbalanced.csv"), key="data.u.hex")

    print("Fit model on dataset.")
        model.unbalanced <- h2o.glm(x=c("x1", "x2"), y="y", data.u.hex, family="binomial", lambda_search=TRUE, use_all_factor_levels=TRUE, alpha=0.5, n_folds=0, higher_accuracy=TRUE, lambda=0)
    print("Check line search invoked even with higher_accuracy off")
        model.unbalanced.ls <- h2o.glm(x=c("x1", "x2"), y="y", data.u.hex, family="binomial", lambda_search=TRUE, use_all_factor_levels=TRUE, alpha=0.5, n_folds=0, higher_accuracy=FALSE, lambda=0)

    print("Extract models' coefficients and assert reasonable values (ie. no greater than 50)")
    print("Unbalanced dataset; higher_accuracy TRUE")
            coef <- model.unbalanced@model$coefficients
        suppressWarnings((coef$"Intercept"<-NULL))
        stopifnot(coef < 50)
    print("Unbalanced dataset; higher_accuracy FALSE")
            coef.ls <- model.unbalanced.ls@model$coefficients
        suppressWarnings((coef.ls$"Intercept"<-NULL))
        stopifnot(coef.ls < 50)

    testEnd()
}

doTest("Testing glm performance on unbalanced synthetic dataset with perfect separation.", test)
