setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises the GBM model downloaded as java code
#           for the iris data set.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------



test.drf.javapredict.cars.rand <-
function() {
    #----------------------------------------------------------------------
    # Parameters for the test.
    #----------------------------------------------------------------------

    # Story:
    # The objective of the test is to verify java code generation
    # for big models containing huge amount of trees.
    # This case verify multi-classifiers.
    training_file <- test_file <- h2oTest.locate("smalldata/junit/cars_nice_header.csv")
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)

    params                 <- list()
    params$ntrees          <- sample( 100, 1)
    params$max_depth       <- sample( 10, 1)
    params$min_rows        <- sample(  20, 1)
    params$balance_classes <- sample( c(T,F), 1)
    params$x               <- c("name","economy", "displacement","power","weight","acceleration","year")
    params$y               <- "cylinders"
    params$training_frame  <- training_frame
    params$seed            <- 42

    h2oTest.doJavapredictTest("randomForest",test_file,test_frame,params)
}

h2oTest.doTest("RF test", test.drf.javapredict.cars.rand)
