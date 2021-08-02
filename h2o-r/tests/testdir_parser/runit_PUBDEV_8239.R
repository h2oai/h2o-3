setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../scripts/h2o-r-test-setup.R")

# Tests parsing with skipped columns
test.parseSkippedColumnsgzip<- function() {
  data <- h2o.importFile(path = "http://h2o-public-test-data.s3.amazonaws.com/smalldata/parser/parquet/as_df_err.parquet")
  
  df <- as.data.frame(data)
    
  expect_equal(nrow(data), nrow(df))
}

doTest("Test Zip Parse with skipped columns", test.parseSkippedColumnsgzip)
