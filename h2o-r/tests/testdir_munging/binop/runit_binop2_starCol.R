setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



toDouble <- function(r) ifelse(is.integer(r), as.numeric(r), r)

test.slice.star <- function() {
  hex <- as.h2o( iris)

  h2oTest.logInfo("Try adding scalar to a numeric column: 5 * hex[,col]")

  col <- sample(ncol(hex), 1)

  h2oTest.logInfo(paste("Using column: ", col))
 
  sliced <- hex[,col]
  h2oTest.logInfo("Placing key \"sliced.hex\" into User Store")
  print(sliced)
  print(class(sliced))

  h2oTest.logInfo("*ing 5 to sliced.hex")
  slicedStarFive <- sliced * 5
  print(head(slicedStarFive))

  h2oTest.logInfo("Checking left and right: ")
  slicedStarFive <- sliced * 5
  fiveStarSliced <- 5 * sliced

  h2oTest.logInfo("sliced * 5: ")
  print(head(slicedStarFive))

  h2oTest.logInfo("5 * sliced: ")
  print(head(fiveStarSliced))

  h2oTest.logInfo("Checking the variation of H2OH2OFrame * H2OH2OFrame")

  hexStarHex <- fiveStarSliced * slicedStarFive

  h2oTest.logInfo("FiveStarSliced * slicedStarFive: ")
  print(head(hexStarHex))
 
  h2oTest.logInfo("as.data.frame(fiveStarSliced) * as.data.frame(fiveStarSliced)")

  
}

h2oTest.doTest("BINOP2 EXEC2 TEST: *", test.slice.star)

