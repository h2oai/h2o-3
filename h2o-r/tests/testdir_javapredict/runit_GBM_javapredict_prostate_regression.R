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
distribution <- "gaussian"
train <- locate("smalldata/logreg/prostate.csv")
test <- locate("smalldata/logreg/prostate.csv")
x = c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON")
y = "CAPSULE"


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
