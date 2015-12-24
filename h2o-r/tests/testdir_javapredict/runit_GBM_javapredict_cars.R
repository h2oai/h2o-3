setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Story:
# The objective of the test is to verify java code generation
# for big models containing huge amount of trees.
# This case verify multi-classifiers.

n.trees <- 5000
interaction.depth <- 10
n.minobsinnode <- 1
shrinkage <- 0.1

train <- locate("smalldata/junit/cars_nice_header.csv")
test <- locate("smalldata/junit/cars_nice_header.csv")

x = c("name","economy", "displacement","power","weight","acceleration","year")
y = "cylinders"


balance_classes <- sample( c(T,F), 1)

heading("Run parameters")
print(paste("n.trees", n.trees))
print(paste("interaction.depth", interaction.depth))
print(paste("n.minobsinnode", n.minobsinnode))
print(paste("shrinkage", shrinkage))
print(paste("train", train))
print(paste("test", test))
print(paste("x=", paste(x, collapse = ", ")))
print(paste("y=", y))
print(paste("balance_classes=", balance_classes))
#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
