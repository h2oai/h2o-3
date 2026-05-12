setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.force_col_types <- function() {
  originalTypes <- c("real", "int", "int", "int", "int", "string", "real", "string", "real", "real", "enum", "int", "int", "int", "int", "enum", 'real', 'real', "enum", "enum", "enum", 'real',  "int", "int", "enum", "enum", "string", "int", "int", "int", "int", "int", "int", "int", "enum", "int", "string", "int", "string", "int", "string",  "string", 'real', "int",  "string", "int", 'real', 'real', "int", "int")
  h2odata <- h2o.importFile(path = locate("smalldata/parser/synthetic_dataset.csv"))
  checkTypes(originalTypes, h2odata)
  
  newType1 <- originalTypes # column type changes happened automatically without force_col_types
  newType1[2] <- "enum"
  newType1[11] <- "int"
  h2odata <- h2o.importFile(path = locate("smalldata/parser/synthetic_dataset.csv"), col.types=newType1)
  checkTypes(newType1, h2odata)
  
  newType2 <- originalTypes # force_col_types not set, no column type changes
  newType2[1] <- "int"
  newType2[2] <- "real"
  newType2[3] <- "real"
  newType2[4] <- "real"
  h2odata <- h2o.importFile(path = locate("smalldata/parser/synthetic_dataset.csv"), col.types=newType2)
  checkTypes(originalTypes, h2odata)
  # col_col_types=TRUE, column type should have changed
  h2odata <- h2o.importFile(path = locate("smalldata/parser/synthetic_dataset.csv"), col.types=newType2, force_col_types=TRUE)
  checkTypes(newType2, h2odata)
}

checkTypes <- function(expectedTypes, dataFrame) {
  numCols <- h2o.ncol(dataFrame)
  newTypes <- h2o.getTypes(dataFrame)
  for (ind in c(1:numCols)) {
    expect_equal(expectedTypes[ind], newTypes[[ind]][1])
  }
}

doTest("Test force_col_types parsing", test.force_col_types)
