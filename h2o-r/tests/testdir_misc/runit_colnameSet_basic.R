##
# Test: colnames<-
# Description: Select a dataset, display column names, assign new colum names
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.basic.colname.assignment <- function() {
  Log.info("Uploading iris data...")
  hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
  hex_name <- h2o.uploadFile(locate("smalldata/iris/iris_header.csv"), "iris_header.hex")

  print(colnames(hex))
  print(colnames(hex_name))
  
  colnames(hex) <- hex_name
  expect_that(colnames(hex), equals(colnames(hex_name)))
  testEnd()
}

doTest("EQ2 Tests: colnames<-", test.basic.colname.assignment)
