setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.gbm.javapredict.cars <- function() {
# Story:
# The objective of the test is to verify java code generation
# for big models containing huge amount of trees.
# This case verify multi-classifiers.

n.trees <- 5000
interaction.depth <- 10
n.minobsinnode <- 1
shrinkage <- 0.1

train <- locate("smalldata/junit/cars_nice_header.csv")
training_frame <- h2o.importFile(train)
test <- locate("smalldata/junit/cars_nice_header.csv")
test_frame <- h2o.importFile(test)

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

params <- list()
params$ntrees <- n.trees
params$max_depth <- interaction.depth
params$min_rows <- n.minobsinnode
params$learn_rate <- shrinkage
params$balance_classes <- balance_classes
params$x <- x
params$y <- y
params$training_frame <- training_frame

doJavapredictTest("gbm",test,test_frame,params)
}
#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_GBM.R')
doTest("GBM",test.gbm.javapredict.cars)