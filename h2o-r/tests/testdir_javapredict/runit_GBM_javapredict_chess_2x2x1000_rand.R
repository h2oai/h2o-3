setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.gbm.javapredict.chess <- function() {
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
training_frame <- h2o.importFile(train)
print(paste("train", train))

test <- locate("smalldata/chess/chess_2x2x1000/test.csv")
test_frame <- h2o.importFile(test)
print(paste("test", test))

x = c("x", "y")
print("x")
print(x)

y = "color"
print(paste("y", y))

params <- list()
params$ntrees <- n.trees
params$max_depth <- interaction.depth
params$min_rows <- n.minobsinnode
params$learn_rate <- shrinkage
params$training_frame <- training_frame
params$x <- x
params$y <- y

doJavapredictTest("gbm",test,test_frame,params)
}


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_GBM.R')

doTest("GBM test",test.gbm.javapredict.chess)
