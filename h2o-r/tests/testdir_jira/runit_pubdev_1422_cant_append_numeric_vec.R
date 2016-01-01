setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
################################################################################
## PUBDEV 1422
## Numeric Column Bugs out
################################################################################



test.cant.assign.to.new.col <- function() {
  h2oTest.logInfo("Works fine on this dataset... Uploading pros...")
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"))

  h2oTest.logInfo("Creating an R vec of randomly uniformly distributed values between 0 and 10 for pros..")
  weights.pros<- runif(nrow(pros.hex), min = 0, max = 10)
  print(weights.pros)
  h2oTest.logInfo("Appending to pros..")
  pros.hex$weights <- as.h2o(weights.pros)

  h2oTest.logInfo("Fails specifically with this dataset... Uploading cars...")
  cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))

  h2oTest.logInfo("Creating an R vec of randomly uniformly distributed values between 0 and 10 for cars..")
  weights.train <- runif(nrow(cars.hex), min = 0, max = 10)
  print(weights.train)
  h2oTest.logInfo("Appending to cars..")
  cars.hex$weights <- as.h2o(weights.train)

  
}

h2oTest.doTest("H2O/R is Failing to Append a Numeric Column", test.cant.assign.to.new.col)
