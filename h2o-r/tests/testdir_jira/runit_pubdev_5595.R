setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev5595 <- function() {
    # Here's a test where we use a weights or fold column to train
    # and then we remove that column for prediction.

    # test where fold_column and weights_column is an integer
    hf_test <- as.h2o(iris)
    iris$fold <- rep(1:5, n = 30)
    hf_train <- as.h2o(iris)

    fit <- h2o.gbm(x = 1:4, y = 5, training_frame = hf_train, fold_column = "fold")
    h2o.predict(fit, newdata = hf_test)  #works

    fit2 <- h2o.gbm(x = 1:4, y = 5, training_frame = hf_train, weights_column = "fold")
    h2o.predict(fit2, newdata = hf_test)  #works


    # test where fold_column is a factor
    hf_test <- as.h2o(iris[,1:4])
    hf_train <- as.h2o(iris)

    fit <- h2o.gbm(x = 1:3, y = 4, training_frame = hf_train, fold_column = "Species")
    h2o.predict(fit, newdata = hf_test)  #works

    data("iris")  #reload iris
    iris$fold <- rep(1:5, n = 30)  #add integer fold col
    hf_train <- as.h2o(iris)
    hf_test <- as.h2o(iris)[,-6]  #remove integer fold col

    fit <- h2o.gbm(x = 1:3, y = 4, training_frame = hf_train, fold_column = "fold")
    h2o.predict(fit, newdata = hf_test)

    fit2 <- h2o.gbm(x = 1:3, y = 4, training_frame = hf_train, weights_column = "fold")
    h2o.predict(fit2, newdata = hf_test)

    data("iris")  #reload iris
    hf_train <- as.h2o(iris)
    hf_test <- as.h2o(iris)[,-5]

    fit <- h2o.gbm(x = 1:3, y = 4, training_frame = hf_train, fold_column = "Species")
    h2o.predict(fit, newdata = hf_test)
}

doTest("PUBDEV-5595: Do not calculate metric on test data", test.pubdev5595)
