setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


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
