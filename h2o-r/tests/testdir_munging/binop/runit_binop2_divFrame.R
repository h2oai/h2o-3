setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




test.div.frame <- function() {
  hex <- as.h2o(iris)
 
  h2oTest.logInfo("Expectations here are to get NaN if 0/0; Inf if nonzero/0; or a number back")
  h2oTest.logInfo("Should get a warning message if there is an enum column and that column should be all NAs")
  
  h2oTest.logInfo("Try hex / 5 : ")
  # if(anyEnum) expect_warning(hexDivFive <- hex / 5)
  # else hexDivFive <- hex / 5
  hexDivFive <- hex / 5
  print(hexDivFive)
  print(head(hexDivFive))
  
  h2oTest.logInfo("Don't expect commutativity, but expect operation to work when operands switched: 5 / hex ")
  # if(anyEnum) expect_warning(fiveDivHex <- 5 / hex)
  # else fiveDivHex <- 5 / hex
  fiveDivHex <- 5 / hex
  print(fiveDivHex)
  print(head(fiveDivHex))
  
  h2oTest.logInfo("Try dividing the frame by itself: hex / hex")
  # if(anyEnum) expect_warning(hexDivHex <- hex / hex)
  # else hexDivHex <- hex / hex
  hexDivHex <- hex / hex
  print(hexDivHex)
  print(head(hexDivHex))
  
  
}

h2oTest.doTest("EXEC2 TEST: BINOP2 test of '/' on frames", test.div.frame)

