setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.gbm.javapredict.iris.pub <- function() {
  heading("Choose PUB-426 parameters")

  n.trees <-944
  interaction.depth <- 4
  n.minobsinnode <- 2
  shrinkage <- 0.2

  train <- locate("smalldata/iris/iris_train.csv")
  training_frame <- h2o.importFile(train)
  test <- locate("smalldata/iris/iris_test.csv")
  test_frame <- h2o.importFile(test)

  x = c("sepal_len","sepal_wid","petal_len","petal_wid");
  y = "species"

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

doTest("GBM test",test.gbm.javapredict.iris.pub)

