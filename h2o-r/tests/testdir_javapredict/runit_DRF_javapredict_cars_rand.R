#----------------------------------------------------------------------
# Purpose:  This test exercises the GBM model downloaded as java code
#           for the iris data set.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

options(echo=FALSE)
TEST_ROOT_DIR <- ".."
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source(paste(TEST_ROOT_DIR, "findNSourceUtils.R", sep="/"))


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Story:
# The objective of the test is to verify java code generation
# for big models containing huge amount of trees. 
# This case verify multi-classifiers.

ntree    <- sample( 100, 1)
depth    <- sample( 100, 1)
nodesize <- sample(  20, 1)
balance_classes <- sample( c(T,F), 1)

train <- locate("smalldata/cars_nice_header.csv")
test <- locate("smalldata/cars_nice_header.csv")

x = c("name","economy", "displacement","power","weight","acceleration","year")
y = "cylinders"

#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_RF.R')
