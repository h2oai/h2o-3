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

n.trees <- 3
interaction.depth <- 1
n.minobsinnode <- 2
shrinkage <- 1
distribution <- "gaussian"
train <- locate("smalldata/smtrees.csv")
test <- locate("smalldata/smtrees.csv")
x = c("girth","height")
y = "vol"


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
