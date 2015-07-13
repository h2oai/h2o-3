setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.eq.frame <- function() {
 hex <- as.h2o( iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex == 'setosa' : ")
  hexEQFive <- hex == "setosa"
  print(head(hexEQFive))
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 'setosa' == hex ")
  fiveEQHex <- "setosa" == hex
  print(head(fiveEQHex))
  
  Log.info("Try >= the frame by itself: hex == hex")
  hexEQHex <- hex == hex
  print(hexEQHex)

  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '==' on frames", test.eq.frame)

