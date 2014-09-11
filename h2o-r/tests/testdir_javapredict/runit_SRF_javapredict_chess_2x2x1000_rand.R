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

ntree <- sample(100, 1)
print(paste("ntree", ntree))

depth <- sample(10, 1)
print(paste("depth", depth))

nodesize <- sample(5, 1)
print(paste("nodesize", nodesize))

train <- locate("smalldata/chess/chess_2x2x1000/R/train.csv")
print(paste("train", train))

test <- locate("smalldata/chess/chess_2x2x1000/R/test.csv")
print(paste("test", test))

x = c("x", "y")
print("x")
print(x)

y = "color"
print(paste("y", y))


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_SRF.R')
