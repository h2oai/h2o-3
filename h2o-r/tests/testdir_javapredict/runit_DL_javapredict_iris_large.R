setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises the DeepLearning model downloaded as java code
#           for the iris data set.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

test.dl.javapredict.iris.large <-
function() {

    training_file <- locate("smalldata/iris/iris_train.csv")
    test_file <- locate("smalldata/iris/iris_test.csv")

#    # AUTOENCODER
#    params                 <- list()
#    params$autoencoder     <- TRUE
#    params$activation      <- "Rectifier"
#    params$hidden          <- c(5,3,2)
#    params$epochs          <- 3
#    params$training_frame  <- training_frame
#
#    params$x               <- c("species","sepal_len","sepal_wid","petal_len","petal_wid")
#    doJavapredictTest("deeplearning",test_file,test_frame,params)
#
#    # only numericals
#    params$x               <- c("sepal_len","sepal_wid","petal_len","petal_wid");
#    doJavapredictTest("deeplearning",test_file,test_frame,params)
#
#    # mixed numericals and categoricals
#    params$x               <- c("species","sepal_len","sepal_wid","petal_len","petal_wid");
#    doJavapredictTest("deeplearning",test_file,test_frame,params)
#
#    activation = "Tanh"
#    params$x               <- c("species","sepal_len","sepal_wid","petal_len","petal_wid");
#    doJavapredictTest("deeplearning",test_file,test_frame,params)
#
#    hidden = c(3)
#    activation = "Tanh"
#    params$x               <- c("species","sepal_len","sepal_wid","petal_len","petal_wid");
#    doJavapredictTest("deeplearning",test_file,test_frame,params)
#
#
    # CLASSIFICATION
    params                 <- list()
    params$autoencoder     <- FALSE
    params$x               <- c("sepal_len","sepal_wid","petal_len","petal_wid")
    params$y               <- "species"

    # large network
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$hidden          <- c(500,500,500)
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    # with imbalance correction
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$hidden          <- c(13,17,50,3)
    params$balance_classes <- TRUE
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    # without imbalance correction
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$balance_classes <- FALSE
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    # other activation functions
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "TanhWithDropout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "Rectifier"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "RectifierWithDropout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "Maxout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "MaxoutWithDropout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    # REGRESSION
    params                 <- list()
    params$autoencoder     <- FALSE
    params$x               <- c("sepal_len","sepal_wid","petal_len")
    params$y               <- "petal_wid"

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "Tanh"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    # other activation functions
    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "TanhWithDropout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "Rectifier"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "RectifierWithDropout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "Maxout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)

    training_frame <- h2o.importFile(training_file)
    test_frame <- h2o.importFile(test_file)
    params$training_frame  <- training_frame
    params$activation      <- "MaxoutWithDropout"
    doJavapredictTest("deeplearning",test_file,test_frame,params)
}

doTest("DL pojo test", test.dl.javapredict.iris.large)