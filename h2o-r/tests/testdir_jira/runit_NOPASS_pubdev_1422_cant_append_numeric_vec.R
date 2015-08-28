################################################################################
## PUBDEV 1422
## Numeric Column Bugs out
################################################################################
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.cant.assign.to.new.col <- function(conn) {
  Log.info("Works fine on this dataset... Uploading pros...")
  pros.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv"))

  Log.info("Creating an R vec of randomly uniformly distributed values between 0 and 10 for pros..")
  weights.pros<- runif(nrow(pros.hex), min = 0, max = 10)
  print(weights.pros)
  Log.info("Appending to pros..")
  pros.hex$weights <- as.h2o(weights.pros)

  Log.info("Fails specifically with this dataset... Uploading cars...")
  cars.hex <- h2o.uploadFile(locate("smalldata/junit/cars.csv"))

  Log.info("Creating an R vec of randomly uniformly distributed values between 0 and 10 for cars..")
  weights.train <- runif(nrow(cars.hex), min = 0, max = 10)
  print(weights.cars)
  Log.info("Appending to cars..")
  cars.hex$weights <- as.h2o(weights.cars)

  testEnd()
}

doTest("H2O/R is Failing to Append a Numeric Column", test.cant.assign.to.new.col)
