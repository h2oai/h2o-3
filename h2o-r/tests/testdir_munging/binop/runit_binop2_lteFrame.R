setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.lte.frame <- function() {
  hex <- as.h2o(iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex <= 5 : ")
  # if(anyEnum) expect_warning(hexLTEFive <- hex <= 5)
  # else hexLTEFive <- hex <= 5
  hexLTEFive <- hex <= 5  

  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 <= hex ")
  # if(anyEnum) expect_warning(fiveLTEHex <- 5 <= hex)
  # else fiveLTEHex <- 5 <= hex
  fiveLTEHex <- 5 <= hex  

  Log.info("Try <= the frame by itself: hex <= hex")
  # if(anyEnum) expect_warning(hexLTEHex <- hex <= hex)
  # else hexLTEHex <- hex <= hex
  hexLTEHex <- hex <= hex

  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '<=' on frames", test.lte.frame)

