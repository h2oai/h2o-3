setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.force_col_types <- function() {
  originalTypes <- c("real", "int") # old H2O parse column tyoes
  h2odata <- h2o.importFile(path = locate("smalldata/parser/parquet/df.parquet"))
  checkTypes(originalTypes, h2odata)
  
  newType1 <- c("real", "real")  # column type changes happened automatically without force_col_types
  h2odata <- h2o.importFile(path = locate("smalldata/parser/parquet/df.parquet"), force_col_types=TRUE)
  checkTypes(newType1, h2odata)
}

checkTypes <- function(expectedTypes, dataFrame) {
  numCols <- h2o.ncol(dataFrame)
  newTypes <- h2o.getTypes(dataFrame)
  for (ind in c(1:numCols)) {
    expect_equal(expectedTypes[ind], newTypes[[ind]][1])
  }
}

doTest("Test force_col_types parsing", test.force_col_types)
