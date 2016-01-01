setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.lte.frame <- function() {
  hex <- as.h2o(iris)
 
  h2oTest.logInfo("Expectation is a frame of booleans")
  
  h2oTest.logInfo("Try hex <= 5 : ")
  # if(anyEnum) expect_warning(hexLTEFive <- hex <= 5)
  # else hexLTEFive <- hex <= 5
  hexLTEFive <- hex <= 5  

  h2oTest.logInfo("Don't expect commutativity, but expect operation to work when operands switched: 5 <= hex ")
  # if(anyEnum) expect_warning(fiveLTEHex <- 5 <= hex)
  # else fiveLTEHex <- 5 <= hex
  fiveLTEHex <- 5 <= hex  

  h2oTest.logInfo("Try <= the frame by itself: hex <= hex")
  # if(anyEnum) expect_warning(hexLTEHex <- hex <= hex)
  # else hexLTEHex <- hex <= hex
  hexLTEHex <- hex <= hex

  
}

h2oTest.doTest("EXEC2 TEST: BINOP2 test of '<=' on frames", test.lte.frame)

