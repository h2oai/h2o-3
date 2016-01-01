setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.head_empty_frame <- function() {

  hex <- as.h2o(iris)
  print(hex)

  hex[1,] <- 3.3
  
  print(hex)
   
  
}

h2oTest.doTest("Test frame add.", test.head_empty_frame)
