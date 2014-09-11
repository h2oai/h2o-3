setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.head_empty_frame <- function(conn) {

  hex <- as.h2o(conn, iris)
  print(hex)

  hex[1,] <- 3.3
  
  print(hex)
   
  testEnd()
}

doTest("Test frame add.", test.head_empty_frame)
