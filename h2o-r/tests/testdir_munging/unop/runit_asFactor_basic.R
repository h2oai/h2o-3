setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.as.factor.basic <- function(conn) {
  hex <- h2o.uploadFile(conn, locate("../smalldata/cars.csv"), key = "cars.hex")
  hex[,"cylinders"] <- as.factor(hex[,"cylinders"])
  expect_true(is.factor(hex[,"cylinders"])[1])
  testEnd()
}

doTest("Test the as.factor unary operator", test.as.factor.basic)

