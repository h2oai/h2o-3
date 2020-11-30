setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Tests Parquet parser by comparing the summary of the original csv frame with the h2o parsed Parquet frame

test.partitionBy <- function() {
  parquet <- h2o.importFile(path = locate("smalldata/partitioned/partitioned_arilines/"), partition_by=c("Year", "IsArrDelayed"))
  csv <- h2o.importFile(path = locate("smalldata/partitioned/partitioned_arilines/"), partition_by=c("Year", "IsArrDelayed"))
  original <- h2o.importFile(path = locate("smalldata/airlines/modified_airlines.csv"))

  expect_equal(dim(original), dim(parquet))
  expect_equal(dim(original), dim(csv))
}

doTest("Test partitioned datasets parsing", test.partitionBy)
