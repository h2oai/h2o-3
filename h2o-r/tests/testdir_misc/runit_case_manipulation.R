setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.string.case.manipulation <- function() {
  h2oTest.logInfo("Importing letters...")
  hex <- as.character(as.h2o(letters))
  print(hex)
  h2oTest.logInfo("Changing to upper case...")
  hex <- h2o.toupper(hex)
  print(hex)
  upper.r <- as.data.frame(hex)[1:26,]
  h2oTest.logInfo("Changing to lower case...")
  hex <- h2o.tolower(hex)
  lower.r <- as.data.frame(hex)[1:26,]
  print(hex)

  expect_equal(upper.r, toupper(letters))
  expect_equal(lower.r, letters)

  
}

h2oTest.doTest("Testing toupper and tolower.", test.string.case.manipulation)
