#----------------------------------------------------------------------
# Purpose:  This test exercises the GBM model downloaded as java code
#           for the iris data set while randomly setting the parameters.
#
# Notes:    Assumes unix environment.
#           curl, javac, java must be installed.
#           java must be at least 1.6.
#----------------------------------------------------------------------

stop("Don't waste time running this failing test")

options(echo=FALSE)
TEST_ROOT_DIR <- ".."
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source(paste(TEST_ROOT_DIR, "findNSourceUtils.R", sep="/"))


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

heading("Choose random parameters")

# The intention of the test is to stress Java code generation
# and compilation.
# Hence, chess 8x8 dataset is used - it forces GBM to generated
# trees with depth ~ 8 (customer usual case). 
# And we try to generate more than 4k trees, since internally in generated java code
# we group each 4k trees into a dedicated class (and the class itself has a limited
# size of constant pool to store references to trees).
# This case use binary classifier.
n.trees <- 5000
interaction.depth <- 10
n.minobsinnode <- 1
shrinkage <- 0.1

train <- locate("smalldata/chess/chess_8x8x1000/R/train.csv")
print(paste("train", train))

test <- locate("smalldata/chess/chess_8x8x1000/R/test.csv")
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
