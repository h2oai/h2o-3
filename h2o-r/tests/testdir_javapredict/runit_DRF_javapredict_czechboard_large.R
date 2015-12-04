setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


#----------------------------------------------------------------------
# Parameters for the test.
test.drf.javapredict.czech <- function() {
ntree <- 100
print(paste(    "ntrees"     , ntree))

depth <- 5
print(paste(    "depth"     , depth))

nodesize <- 10
print(paste( "nodesize", nodesize))

train <- locate("smalldata/gbm_test/czechboard_300x300.csv")
training_frame <- h2o.importFile(train)
print(paste(    "train"     , train))

test <- locate("smalldata/gbm_test/czechboard_300x300.csv")
test_frame <- h2o.importFile(test)
print(paste(    "test"     , test))

x = c("C1", "C2")
print(    "x"     )
print(x)

y = "C3"
print(paste(    "y" , y))

params <- list()
params$ntrees <- ntree
params$max_depth <- depth
params$min_rows <- nodesize
params$training_frame <- training_frame
params$x <- x
params$y <- y

doJavapredictTest("randomForest",test,test_frame,params)

}
#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_RF.R')
doTest("RF test", test.drf.javapredict.czech)
