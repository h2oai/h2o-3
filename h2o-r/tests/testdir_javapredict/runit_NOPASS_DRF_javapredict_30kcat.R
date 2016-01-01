setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises the RF model downloaded as java code
#           for the dhisttest data set. It checks whether the generated
#           java correctly splits categorical predictors into non-
#           contiguous groups at each node.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

options(echo=FALSE)
TEST_ROOT_DIR <- ".."




#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

ntree <- 50
print(paste(    "ntrees"     , ntree))

depth <- 5
print(paste(    "depth"     , depth))

nodesize <- 10
print(paste( "nodesize", nodesize))

train <- h2oTest.locate("smalldata/gbm_test/30k_cattest.csv")
print(paste(    "train"     , train))

test <- h2oTest.locate("smalldata/gbm_test/30k_cattest.csv")
print(paste(    "test"     , test))

x = c("C1", "C2")
print(    "x"     )
print(x)

y = "C3"
print(paste(    "y" , y))


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_RF.R')
