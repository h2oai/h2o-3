setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Tests Parquet parser by comparing the summary of the original csv frame with the h2o parsed Parquet frame

test.parseSimple <- function() {
  col.names <- c("fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime", "ArrTime", "UniqueCarrier", "Origin", "Dest", "Distance", "IsDepDelayed", "IsDepDelayed_REC")
  col.types <- c("String", "String", "String", "String", "Numeric", "Numeric", "String", "String", "String", "Numeric", "String", "Numeric")
  
  csv.input <- locate("smalldata/airlines/AirlinesTrain.csv.zip")
  parquet.input <- locate("smalldata/parser/parquet/airlines-simple.snappy.parquet")

  csv = h2o.importFile(csv.input, destination_frame = "csv", col.names = col.names, col.types = col.types, header = TRUE)

  parquet = h2o.importFile(parquet.input, destination_frame = "parquet", col.names = col.names, col.types = col.types)

  expect_equal(dim(csv), dim(parquet))
  expect_equal(summary(csv, exact_quantiles=TRUE), summary(parquet, exact_quantiles=TRUE))
}

doTest("Test Parquet parser: simple case - only string/numerical columns", test.parseSimple)