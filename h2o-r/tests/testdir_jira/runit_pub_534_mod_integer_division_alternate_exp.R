setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.ops<- function() {

  #  should support: 
  # ^ or ** exponentiation 
  # x %% y modulus (x mod y) 5%%2 is 1 
  # x %/% y integer division 5%/%2 is 2 

  hex <- as.h2o(iris)
  hex ** 2
  hex %% 3
  hex %/% 3.14

  
}

h2oTest.doTest("test %/%, %%, **", test.ops)
