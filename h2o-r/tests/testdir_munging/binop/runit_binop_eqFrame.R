setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.eq.frame <- function() {
  hex <- as.h2o( iris)
 
  h2oTest.logInfo("Expectation is a frame of booleans")
  
  h2oTest.logInfo("Try hex == 'setosa' : ")
  hexEQFive <- hex == "setosa"
  print(head(hexEQFive))
  
  h2oTest.logInfo("Don't expect commutativity, but expect operation to work when operands switched: 'setosa' == hex ")
  fiveEQHex <- "setosa" == hex
  print(head(fiveEQHex))
  
  h2oTest.logInfo("Try >= the frame by itself: hex == hex")
  hexEQHex <- hex == hex
  print(hexEQHex)

  
}

h2oTest.doTest("EXEC2 TEST: BINOP2 test of '==' on frames", test.eq.frame)

