setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

ntree <- 50
print(paste(    "ntrees"     , ntree))

depth <- 5
print(paste(    "depth"     , depth))

nodesize <- 10
print(paste( "nodesize", nodesize))

train <- locate("smalldata/gbm_test/30k_cattest.csv")
print(paste(    "train"     , train))

test <- locate("smalldata/gbm_test/30k_cattest.csv")
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
