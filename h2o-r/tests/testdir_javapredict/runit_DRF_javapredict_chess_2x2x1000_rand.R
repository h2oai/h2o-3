setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------
test.drf.javapredict.chess <- function() {
  heading("Choose random parameters")

  ntree <- sample(100, 1)


  depth <- sample(10, 1)


  nodesize <- sample(5, 1)


  train <- locate("smalldata/chess/chess_2x2x1000/train.csv")

  training_frame <- h2o.importFile(train)

  test <- locate("smalldata/chess/chess_2x2x1000/test.csv")

  test_frame <- h2o.importFile(test)

  x = c("x", "y")


  y = "color"


  params <- list()
  params$ntrees <- ntree
  params$max_depth <- depth
  params$min_rows <- nodesize
  params$x <- x
  params$y <- y
  params$training_frame <- training_frame

  doJavapredictTest("randomForest",test,test_frame,params)
    print(paste("ntree", ntree))
    print(paste("depth", depth))
    print(paste("nodesize", nodesize))
    print(paste("train", train))
    print(paste("test", test))
    print("x")
    print(x)
    print(paste("y", y))

}


#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_RF.R')
doTest("RF test",test.drf.javapredict.chess)