setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##################################
## basic regression test
##################################





test.regression.basic <- function() {
  cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])

  cars.drf <- h2o.randomForest(x = 3:7, y = 2, cars.hex)
  print(cars.drf)

  
}

h2oTest.doTest("Basic Regession using Random Forest", test.regression.basic)
