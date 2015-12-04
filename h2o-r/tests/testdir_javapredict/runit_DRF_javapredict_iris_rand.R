setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


     #----------------------------------------------------------------------
     # Parameters for the test.
     #----------------------------------------------------------------------
test.gbm.javapredict.iris.rand <- function() {
heading("Choose random parameters" )

ntree <- sample(1000 ,1 )
print(paste(    "ntrees"     , ntree))

depth <- sample(   999     ,    1     )
print(paste(    "depth"     , depth))

nodesize <- sample(   10     ,    1     )
print(paste( "nodesize", nodesize))

train <- locate(    "smalldata/iris/iris_train.csv"     )
print(paste(    "train"     , train))
training_frame <- h2o.importFile(train)

test <- locate(    "smalldata/iris/iris_test.csv"     )
print(paste(    "test"     , test))
test_frame <- h2o.importFile(test)

x = c(    "sepal_len"     ,    "sepal_wid"     ,    "petal_len"     ,    "petal_wid"     );
print(    "x"     )
print(x)

y = "species"
print(paste(    "y" , y))

params <- list()
params$ntrees <- ntree
params$max_depth <- depth
params$min_rows <- nodesize
params$x <- x
params$y <- y
params$training_frame <- training_frame

doJavapredictTest("gbm",test,test_frame,params)
}

     #----------------------------------------------------------------------
     # Run the test
     #----------------------------------------------------------------------
#source(   '../Utils/shared_javapredict_RF.R'   )
doTest("GBM test",test.gbm.javapredict.iris.rand)
