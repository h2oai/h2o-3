setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test.lt.frame <- function() {
  hex <- as.h2o( iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex < 5 : ")
  # if(anyEnum) expect_warning(hexLTFive <- hex < 5)
  # else hexLTFive <- hex < 5
  hexLTFive <- hex < 5
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 < hex ")
  # if(anyEnum) expect_warning(fiveLTHex <- 5 < hex)
  # else fiveLTHex <- 5 < hex
  fiveLTHex <- 5 < hex
  
  Log.info("Try < the frame by itself: hex < hex")
  # if(anyEnum) expect_warning(hexLTHex <- hex < hex)
  # else hexLTHex <- hex < hex
  hexLTHex <- hex < hex
  
  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '<' on frames", test.lt.frame)

