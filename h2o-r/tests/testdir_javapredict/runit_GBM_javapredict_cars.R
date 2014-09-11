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

# Story:
# The objective of the test is to verify java code generation
# for big models containing huge amount of trees. 
# This case verify multi-classifiers.

n.trees <- 5000
interaction.depth <- 10
n.minobsinnode <- 1
shrinkage <- 0.1

train <- locate("smalldata/cars_nice_header.csv")
test <- locate("smalldata/cars_nice_header.csv")

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
print(paste("x=", x))
print(paste("y=", y))
print(paste("balance_classes=", balance_classes))
#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_GBM.R')
