setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

######################################################################
#Define some helper functions to get RMSLE manually

# Compute the squared log error
#
# This function computes the elementwise squared log error for a
# number or a vector
#
# @param actual ground truth number or vector
# @param predicted predicted number or vector
sle <- function (actual, predicted) (log(1+actual)-log(1+predicted))^2

# Compute the mean squared log error
#
# This function computes the mean squared log error between
# two vectors
#
# @param actual ground truth vector
# @param predicted predicted vector
msle <- function (actual, predicted) mean(sle(actual, predicted))

# Compute the root mean squared log error
#
# This function computes the root mean squared log error between
# two vectors
#
# @param actual ground truth vector
# @param predicted predicted vector
rmsle <- function (actual, predicted) sqrt(msle(actual, predicted))
######################################################################

rmsle_test = function(){
    #Define some random data
    actual = runif(1e5)
    pred = runif(1e5)

    #Compute RMSLE using above helper functions
    rmsle_r = rmsle(actual,pred)
    print(paste0("RMSLE in R: ",rmsle_r))

    #Compute RMSLE using H2O
    model_metrics = h2o.make_metrics(as.h2o(pred),as.h2o(actual))
    rmsle_h2o = h2o.rmsle(model_metrics)
    print(paste0("RMSLE in H2O ", rmsle_h2o))

    #Test
    expect_true(abs(rmsle_r-rmsle_h2o)<1e-5)
 }
doTest("Check RMSLE Calculation in H2O.", rmsle_test)