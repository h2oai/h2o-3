##################################
## basic regression test
##################################


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.regression.basic <- function() {
  cars.hex <- h2o.uploadFile(locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])

  cars.drf <- h2o.randomForest(x = 3:7, y = 2, cars.hex)
  print(cars.drf)

  
}

doTest("Basic Regession using Random Forest", test.regression.basic)
