setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.binop2.ampersand <- function() {

  hex <- as.h2o(iris)

  print(hex & 5)
  print(5 & hex)
  #print(5 && hex)
  #print(hex && 5)
  #print(hex && c(5,10,20))
  #print(c(5,10,20) && hex)
  #print(hex[,1] && c(5,10,20))
  #print(c(5,10,20) && hex[,1])
  #print(hex[,1] & c(5,10,20))
  #print(c(5,10,20) & hex[,1])
  
  
}

h2oTest.doTest("Binop2 EQ2 Test: & and &&", test.binop2.ampersand)

