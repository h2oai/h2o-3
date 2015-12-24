setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

heading("Choose PUB-426 parameters")

n.trees <-944
interaction.depth <- 4
n.minobsinnode <- 2
shrinkage <- 0.2

train <- locate("smalldata/iris/iris_train.csv")
test <- locate("smalldata/iris/iris_test.csv")

x = c("sepal_len","sepal_wid","petal_len","petal_wid");
y = "species"


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
