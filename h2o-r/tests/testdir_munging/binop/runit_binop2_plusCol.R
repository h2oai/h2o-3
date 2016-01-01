setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")





colPlus.numeric <- function() {
  hex <- as.h2o( iris)
  col <- 1
  h2oTest.logInfo(paste("Using column: ", col))
 
  sliced <- hex[,col]
  h2oTest.logInfo("Placing key \"sliced.hex\" into User Store")

  h2oTest.logInfo("Adding 5 to sliced.hex")
  slicedPlusFive <- sliced + 5

  h2oTest.logInfo("Orignal sliced: ")
  print(head(as.data.frame(sliced)))

  h2oTest.logInfo("Sliced + 5: ")
  print(head(as.data.frame(slicedPlusFive)))
  expect_that(as.data.frame(slicedPlusFive), equals(5 + as.data.frame(sliced)))

  h2oTest.logInfo("Checking left and right: ")
  slicedPlusFive <- sliced + 5

  fivePlusSliced <- 5 + sliced

  h2oTest.logInfo("sliced + 5: ")
  print(head(slicedPlusFive))

  h2oTest.logInfo("5 + sliced: ")
  print(head(fivePlusSliced))
  expect_that(as.data.frame(slicedPlusFive), equals(as.data.frame(fivePlusSliced)))


  h2oTest.logInfo("Checking the variation of H2OH2OFrame + H2OH2OFrame")

  hexPlusHex <- fivePlusSliced + slicedPlusFive

  h2oTest.logInfo("FivePlusSliced + slicedPlusFive: ")
  print(head(hexPlusHex))
  expect_that(as.data.frame(hexPlusHex), equals(2*as.data.frame(fivePlusSliced)))

  
}

h2oTest.doTest("Column Addition With Scaler", colPlus.numeric)

