setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.model.gam <- function() {
    data <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/gam_test/decreasingCos.csv")
    test <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/gam_test/nondecreasingCosTest.csv")
    gamCol <- c("a")
    decreasingModel <- h2o.gam(x=c(), y = "cosy", gam_columns = gamCol, bs=c(2), training_frame = data, splines_non_negative=c(FALSE))
    predFrame <- h2o.predict(decreasingModel, test)
    assertMonotonicDecreasing(test, predFrame)
    increasingModel <- h2o.gam(x=c(), y = "cosy", gam_columns = gamCol, bs=c(2), training_frame = data, splines_non_negative=c(TRUE))
    coefs <- as.numeric(increasingModel@model$coefficients)
    coefsLen <- length(coefs)-1
    # should be zero except intercept because dataset is monotonically decreasing
    for (ind in c(1:coefsLen)) {
        expect_true(coefs[ind]==0)
    }
}

assertMonotonicDecreasing <- function(test, predFrame) {
    # check making sure prediction is increasing
    pResult = unlist(as.data.frame(predFrame$predict), use.names=FALSE)
    nrow <- h2o.nrow(predFrame)
    vals <- unlist(as.data.frame(test$a), use.names=FALSE)
    for (rowInd in c(2:nrow)) {
        val1 <- vals[rowInd-1]
        val2 <- vals[rowInd]
        resp1 <- pResult[rowInd-1]
        resp2 <- pResult[rowInd]
        expect_true(resp1 >= resp2)
    }
}

doTest("General Additive Model test", test.model.gam)
