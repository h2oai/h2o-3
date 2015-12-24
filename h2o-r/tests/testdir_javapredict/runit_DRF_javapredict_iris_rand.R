setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


     #----------------------------------------------------------------------
     # Parameters for the test.
     #----------------------------------------------------------------------

heading("Choose random parameters" )

ntree <- sample(1000 ,1 )
print(paste(    "ntrees"     , ntree))

depth <- sample(   999     ,    1     )
print(paste(    "depth"     , depth))

nodesize <- sample(   10     ,    1     )
print(paste( "nodesize", nodesize))

train <- locate(    "smalldata/iris/iris_train.csv"     )
print(paste(    "train"     , train))

test <- locate(    "smalldata/iris/iris_test.csv"     )
print(paste(    "test"     , test))

x = c(    "sepal_len"     ,    "sepal_wid"     ,    "petal_len"     ,    "petal_wid"     );
print(    "x"     )
print(x)

y = "species"
print(paste(    "y" , y))


     #----------------------------------------------------------------------
     # Run the test
     #----------------------------------------------------------------------
source(   '../Utils/shared_javapredict_RF.R'   )
