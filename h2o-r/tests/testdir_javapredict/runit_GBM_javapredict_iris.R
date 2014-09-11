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

n.trees <- 100 
interaction.depth <- 5
n.minobsinnode <- 10
shrinkage <- 0.1
train <- locate("smalldata/iris/iris_train.csv")
test <- locate("smalldata/iris/iris_test.csv")
x = c("sepal_len","sepal_wid","petal_len","petal_wid");
y = "species"


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
