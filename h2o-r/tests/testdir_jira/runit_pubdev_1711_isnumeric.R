setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.is.numeric <- function(h) {
    iris_h2o <- as.h2o(iris)
    expect_true(is.numeric(iris_h2o[,1]))
    expect_true(is.numeric(as.numeric(iris_h2o[,1])))

  	
}

doTest("Test pubdev-1711", test.pubdev.is.numeric )
