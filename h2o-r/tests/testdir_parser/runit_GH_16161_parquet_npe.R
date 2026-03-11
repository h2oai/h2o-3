setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.parseParquetString<- function() {
  df <- h2o.createFrame(rows = 100,
    cols = 10,
    string_fraction = 0.1, # create one string column
    seed = 5,
    seed_for_column_types = 25)
  target <- file.path(sandbox(), "createdFrame.parquet")
  h2o.exportFile(data = df,
    path = target,
    format = "parquet",
    write_checksum = FALSE)
  df2 <- h2o.importFile(target)
  compareFrames(df, df2)
}

doTest("Test Parquet String export error.", test.parseParquetString)
