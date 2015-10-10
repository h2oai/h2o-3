setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.string.case.manipulation <- function() {
  Log.info("Importing letters...")
  hex <- as.character(as.h2o(letters))
  print(hex)
  Log.info("Changing to upper case...")
  hex <- h2o.toupper(hex)
  print(hex)
  upper.r <- as.data.frame(hex)[1:26,]
  Log.info("Changing to lower case...")
  hex <- h2o.tolower(hex)
  lower.r <- as.data.frame(hex)[1:26,]
  print(hex)

  expect_equal(upper.r, toupper(letters))
  expect_equal(lower.r, letters)

  
}

doTest("Testing toupper and tolower.", test.string.case.manipulation)
