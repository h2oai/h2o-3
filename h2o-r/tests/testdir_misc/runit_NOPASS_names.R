setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test: name is added as method for H2OH2OFrame properly
# Description: push a dataset into H2O and convert column names with duplicates sould throw an error
##



test.names <- function() {
  
  Log.info("Importing heart dataset into H2O...")
  iris.hex <- as.h2o(iris, "iris.hex")
  Log.info("Define incorrect column names...")
  new_col  <- c("Column1", "Column2", "Column3", "Column4", "Column1")
  Log.info("Attempt to rename column names...")
  checkException(names( iris.hex) <- new_col, "Renaming with duplicate column names should throw an error...", silent = T)
  
}

doTest("R and H2O name Function", test.names)

