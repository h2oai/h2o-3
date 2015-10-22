setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

toDouble <- function(r) ifelse(is.integer(r), as.numeric(r), r)

test.slice.star <- function() {
  hex <- as.h2o( iris)

  Log.info("Try adding scalar to a numeric column: 5 * hex[,col]")

  col <- sample(ncol(hex), 1)

  Log.info(paste("Using column: ", col))
 
  sliced <- hex[,col]
  Log.info("Placing key \"sliced.hex\" into User Store")
  print(sliced)
  print(class(sliced))

  Log.info("*ing 5 to sliced.hex")
  slicedStarFive <- sliced * 5
  print(head(slicedStarFive))

  Log.info("Checking left and right: ")
  slicedStarFive <- sliced * 5
  fiveStarSliced <- 5 * sliced

  Log.info("sliced * 5: ")
  print(head(slicedStarFive))

  Log.info("5 * sliced: ")
  print(head(fiveStarSliced))

  Log.info("Checking the variation of H2OFrame * H2OFrame")

  hexStarHex <- fiveStarSliced * slicedStarFive

  Log.info("FiveStarSliced * slicedStarFive: ")
  print(head(hexStarHex))
 
  Log.info("as.data.frame(fiveStarSliced) * as.data.frame(fiveStarSliced)")

  
}

doTest("BINOP2 EXEC2 TEST: *", test.slice.star)

