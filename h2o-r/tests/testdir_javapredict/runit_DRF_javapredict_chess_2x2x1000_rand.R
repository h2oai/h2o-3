setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


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

train <- locate("smalldata/chess/chess_2x2x1000/train.csv")
print(paste("train", train))

test <- locate("smalldata/chess/chess_2x2x1000/test.csv")
print(paste("test", test))

x = c("x", "y")
print("x")
print(x)

y = "color"
print(paste("y", y))


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
source('../Utils/shared_javapredict_RF.R')
