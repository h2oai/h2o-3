#----------------------------------------------------------------------
# Purpose:  This test exercises the GBM model downloaded as java code
#           for the iris data set while randomly setting the parameters.
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

heading("Choose random parameters")

n.trees <- sample(1000, 1)
print(paste("n.trees", n.trees))

interaction.depth <- sample(5, 1)
print(paste("interaction.depth", interaction.depth))

n.minobsinnode <- sample(10, 1)
print(paste("n.minobsinnode", n.minobsinnode))

shrinkage <- sample(c(0.001, 0.002, 0.01, 0.02, 0.1, 0.2), 1)
print(paste("shrinkage", shrinkage))

train <- locate("smalldata/iris/iris_train.csv")
print(paste("train", train))

test <- locate("smalldata/iris/iris_test.csv")
print(paste("test", test))

x = c("sepal_len","sepal_wid","petal_len","petal_wid");
print("x")
print(x)

y = "species"
print(paste("y", y))


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
