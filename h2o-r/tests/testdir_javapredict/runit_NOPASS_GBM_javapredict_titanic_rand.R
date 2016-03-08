setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises the GBM model downloaded as java code
#           for the iris data set while randomly setting the parameters.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

test.gbm.javapredict.titanic.rand <-
function() {

    training_file <- locate("smalldata/gbm_test/titanic.csv")
    test_file <- locate("smalldata/gbm_test/titanic.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntree           <- sample(1000, 1)
    params$max_depth       <- sample(5, 1)
    params$min_rows        <- sample(10, 1)
    params$learn_rate      <- sample(c(0.001, 0.002, 0.01, 0.02, 0.1, 0.2), 1)
    params$balance_classes <- sample(c(T, F), 1)
    params$x               <- c("pclass","name","sex","age","fare")
    params$y               <- "survived"
    params$training_frame  <- training_frame

    doJavapredictTest("gbm",test_file,test_frame,params)
}

doTest("GBM pojo test", test.gbm.javapredict.titanic.rand)
