setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.minus <- function(conn) {
  hex <- as.h2o(conn, iris)

  Log.info("Try adding scalar to a numeric column: 5 - hex[,col]")



  df <- head(hex)
  col <- sample(ncol(hex), 1)
  Log.info(paste("Using column: ", col))
 
  sliced <- hex[,col]
  Log.info("Placing key \"sliced.hex\" into User Store")

  Log.info("Minisuing 5 from sliced.hex")
  slicedMinusFive <- sliced - 5

  Log.info("Original sliced: ")
  print(head((sliced)))

  Log.info("Sliced - 5: ")
  print(head((slicedMinusFive)))

  Log.info("Checking left and right: ")
  fiveMinusSliced <- 5 - sliced

  Log.info("5 - sliced: ")
  print(head(fiveMinusSliced))

  Log.info("Checking the variation of h2o.frame - h2o.frame")

  hexMinusHex <- fiveMinusSliced - slicedMinusFive

  Log.info("fiveMinusSliced - slicedMinusFive: ")
  print(head(hexMinusHex))

  testEnd()
}

doTest("BINOP2 EXEC2 TEST: '-'", test.minus)

