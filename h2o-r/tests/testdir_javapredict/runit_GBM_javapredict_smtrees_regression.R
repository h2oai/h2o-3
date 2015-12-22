setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

n.trees <- 3
interaction.depth <- 1
n.minobsinnode <- 2
shrinkage <- 1
distribution <- "gaussian"
train <- locate("smalldata/gbm_test/smtrees.csv")
test <- locate("smalldata/gbm_test/smtrees.csv")
x = c("girth","height")
y = "vol"


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
