##
# Testing '+' expression for h2o data objects
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test <- function(conn) {

  print("Reading small airline data to train and test into H2O")
  airline.test.hex = h2o.importFile(conn, locate("smalldata/airlines/allyears2k_headers.zip"), key="airline.test.hex", header=TRUE)
  print("Reading UUIDs into H2O")
  uuid.hex = h2o.importFile(conn, locate("smalldata/airlines/airlineUUID.csv"), key="uuid.hex", header=TRUE)
  
  print("Error with splice UUID to both predictions :: '+' operator")
  assertError(air.uuid <- h2o.assign((airline.test.hex + uuid.hex), key="air.uuid"))
  
  testEnd()
}

doTest("Testing '+' expression for h2o data objects", test)
