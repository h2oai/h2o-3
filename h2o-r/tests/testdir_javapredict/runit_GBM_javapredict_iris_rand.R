setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.gbm.javapredict.iris.rand <- function() {
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
training_frame = h2o.importFile(train)
print(paste("train", train))

test <- locate("smalldata/iris/iris_test.csv")
test_frame <- h2o.importFile(test)
print(paste("test", test))

x = c("sepal_len","sepal_wid","petal_len","petal_wid");
print("x")
print(x)

y = "species"
print(paste("y", y))

params <- list()
params$ntrees <- n.trees
params$max_depth <- interaction.depth
params$min_rows <- n.minobsinnode
params$learn_rate <- shrinkage
params$x <- x
params$y <- y
params$training_frame <- training_frame

doJavapredictTest("gbm",test,test_frame,params)
}


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_GBM.R')
doTest("GBM test",test.gbm.javapredict.iris.rand)
