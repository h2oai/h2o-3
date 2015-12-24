setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

heading("Choose random parameters")

n.trees <- sample(100, 1)
print(paste("n.trees", n.trees))

interaction.depth <- sample(10, 1)
print(paste("interaction.depth", interaction.depth))

n.minobsinnode <- sample(5, 1)
print(paste("n.minobsinnode", n.minobsinnode))

shrinkage <- sample(c(0.001, 0.002, 0.01, 0.02, 0.1, 0.2), 1)
print(paste("shrinkage", shrinkage))

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
source('../Utils/shared_javapredict_GBM.R')
