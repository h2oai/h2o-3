setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Testing '+' expression for h2o data objects
##




test <- function() {

  print("Reading small airline data to train and test into H2O")
  airline.test.hex = h2o.importFile(h2oTest.locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="airline.test.hex", header=TRUE)
  print("Reading UUIDs into H2O")
  uuid.hex = h2o.importFile(h2oTest.locate("smalldata/airlines/airlineUUID.csv"), destination_frame="uuid.hex", header=TRUE)
  
  print("Error with splice UUID to both predictions :: '+' operator")
  assertError(air.uuid <- h2o.assign((airline.test.hex + uuid.hex), key="air.uuid"))
  
  
}

h2oTest.doTest("Testing '+' expression for h2o data objects", test)
