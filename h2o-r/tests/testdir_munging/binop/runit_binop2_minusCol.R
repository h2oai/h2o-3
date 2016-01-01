setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.minus <- function() {
  hex <- as.h2o( iris)

  h2oTest.logInfo("Try adding scalar to a numeric column: 5 - hex[,col]")



  df <- head(hex)
  col <- sample(ncol(hex), 1)
  h2oTest.logInfo(paste("Using column: ", col))
 
  sliced <- hex[,col]
  h2oTest.logInfo("Placing key \"sliced.hex\" into User Store")

  h2oTest.logInfo("Minisuing 5 from sliced.hex")
  slicedMinusFive <- sliced - 5

  h2oTest.logInfo("Original sliced: ")
  print(head((sliced)))

  h2oTest.logInfo("Sliced - 5: ")
  print(head((slicedMinusFive)))

  h2oTest.logInfo("Checking left and right: ")
  fiveMinusSliced <- 5 - sliced

  h2oTest.logInfo("5 - sliced: ")
  print(head(fiveMinusSliced))

  h2oTest.logInfo("Checking the variation of H2OH2OFrame - H2OH2OFrame")

  hexMinusHex <- fiveMinusSliced - slicedMinusFive

  h2oTest.logInfo("fiveMinusSliced - slicedMinusFive: ")
  print(head(hexMinusHex))

  
}

h2oTest.doTest("BINOP2 EXEC2 TEST: '-'", test.minus)

