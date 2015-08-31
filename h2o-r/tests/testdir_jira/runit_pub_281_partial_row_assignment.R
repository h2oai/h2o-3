setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.head_empty_frame <- function() {

  hex <- as.h2o(iris)
  print(hex)

  hex[1,] <- 3.3
  
  print(hex)
   
  testEnd()
}

doTest("Test frame add.", test.head_empty_frame)
