
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


colPlus.numeric <- function() {
  hex <- as.h2o( iris)
  col <- 1
  Log.info(paste("Using column: ", col))
 
  sliced <- hex[,col]
  Log.info("Placing key \"sliced.hex\" into User Store")

  Log.info("Adding 5 to sliced.hex")
  slicedPlusFive <- sliced + 5

  Log.info("Orignal sliced: ")
  print(head(as.data.frame(sliced)))

  Log.info("Sliced + 5: ")
  print(head(as.data.frame(slicedPlusFive)))
  expect_that(as.data.frame(slicedPlusFive), equals(5 + as.data.frame(sliced)))

  Log.info("Checking left and right: ")
  slicedPlusFive <- sliced + 5

  fivePlusSliced <- 5 + sliced

  Log.info("sliced + 5: ")
  print(head(slicedPlusFive))

  Log.info("5 + sliced: ")
  print(head(fivePlusSliced))
  expect_that(as.data.frame(slicedPlusFive), equals(as.data.frame(fivePlusSliced)))


  Log.info("Checking the variation of H2OFrame + H2OFrame")

  hexPlusHex <- fivePlusSliced + slicedPlusFive

  Log.info("FivePlusSliced + slicedPlusFive: ")
  print(head(hexPlusHex))
  expect_that(as.data.frame(hexPlusHex), equals(2*as.data.frame(fivePlusSliced)))

  
}

doTest("Column Addition With Scaler", colPlus.numeric)

