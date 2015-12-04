setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


# Story:
# The objective of the test is to verify java code generation
# for big models containing huge amount of trees.
# This case verify multi-classifiers.
test.drf.javapredict.cars.rand <- function() {
ntree    <- sample( 100, 1)
depth    <- sample( 100, 1)
nodesize <- sample(  20, 1)
balance_classes <- sample( c(T,F), 1)

train <- locate("smalldata/junit/cars_nice_header.csv")
training_frame <- h2o.importFile(train)
test <- locate("smalldata/junit/cars_nice_header.csv")
test_frame <- h2o.importFile(test)

x = c("name","economy", "displacement","power","weight","acceleration","year")
y = "cylinders"

params <- list()
params$ntrees <- ntree
params$max_depth <- depth
params$min_rows <- nodesize
params$balance_classes <- balance_classes
params$training_frame <- training_frame
params$x <- x
params$y <- y
params$seed <- 42

doJavapredictTest("randomForest",test,test_frame,params)
}

#----------------------------------------------------------------------
# Run the test
#----------------------------------------------------------------------
#source('../Utils/shared_javapredict_RF.R')
doTest("RF test",test.drf.javapredict.cars.rand)

