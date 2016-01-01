setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.gt.frame <- function() {
  hex <- as.h2o(iris)
 
  h2oTest.logInfo("Expectation is a frame of booleans")
  
  h2oTest.logInfo("Try hex > 5 : ")
  hexGTFive <- hex > 5 
  print(head(hexGTFive)) 

  h2oTest.logInfo("Don't expect commutativity, but expect operation to work when operands switched: 5 > hex ")
  fiveGTHex <- 5 > hex
  print(head(fiveGTHex))

  h2oTest.logInfo("Try > the frame by itself: hex > hex")
  hex > hex
  print(head(hex > hex))

  
}

h2oTest.doTest("EXEC2 TEST: BINOP2 test of '>' on frames", test.gt.frame)

