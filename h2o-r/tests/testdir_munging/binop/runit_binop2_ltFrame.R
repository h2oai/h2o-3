setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.lt.frame <- function() {
  hex <- as.h2o( iris)
 
  h2oTest.logInfo("Expectation is a frame of booleans")
  
  h2oTest.logInfo("Try hex < 5 : ")
  # if(anyEnum) expect_warning(hexLTFive <- hex < 5)
  # else hexLTFive <- hex < 5
  hexLTFive <- hex < 5
  
  h2oTest.logInfo("Don't expect commutativity, but expect operation to work when operands switched: 5 < hex ")
  # if(anyEnum) expect_warning(fiveLTHex <- 5 < hex)
  # else fiveLTHex <- 5 < hex
  fiveLTHex <- 5 < hex
  
  h2oTest.logInfo("Try < the frame by itself: hex < hex")
  # if(anyEnum) expect_warning(hexLTHex <- hex < hex)
  # else hexLTHex <- hex < hex
  hexLTHex <- hex < hex
  
  
}

h2oTest.doTest("EXEC2 TEST: BINOP2 test of '<' on frames", test.lt.frame)

