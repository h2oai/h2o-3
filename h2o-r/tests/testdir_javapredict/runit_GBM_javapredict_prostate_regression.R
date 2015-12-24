setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


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
