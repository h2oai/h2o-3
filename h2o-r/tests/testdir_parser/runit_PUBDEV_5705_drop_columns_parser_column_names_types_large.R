setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumnsNameType <- function() {
  csvWithHeader <-
    h2o.importFile(locate("smalldata/airlines/allyears2k_headers_8kRows.csv.zip"))
  allColnames <- h2o.names(csvWithHeader)
  allTypeDict <- h2o.getTypes(csvWithHeader)

  pathNoHeader <- locate("smalldata/airlines/allyears2k_8kRows.csv.zip")

  skip_front <- c(1)
  skip_end <- c(h2o.ncol(csvWithHeader))
  set.seed <- 12345
  onePermute <- sample(h2o.ncol(csvWithHeader))
  skipall <- onePermute
  skip90Per <- onePermute[1:floor(h2o.ncol(csvWithHeader) * 0.9)]

  # skip 90% of the columns randomly
  print("Testing skipping 90% of columns")
  assertCorrectSkipColumnsNamesTypes(csvWithHeader, pathNoHeader, skip90Per, allColnames, allTypeDict,0, h2o.getTypes(csvWithHeader))
  assertCorrectSkipColumnsNamesTypes(csvWithHeader, pathNoHeader, skip90Per, allColnames, allTypeDict,1, h2o.getTypes(csvWithHeader))
  assertCorrectSkipColumnsNamesTypes(csvWithHeader, pathNoHeader, skip90Per, allColnames, allTypeDict,2, h2o.getTypes(csvWithHeader))
}

doTest("Test Parse with column names and column types specified with skipped columns", test.parseSkippedColumnsNameType)
