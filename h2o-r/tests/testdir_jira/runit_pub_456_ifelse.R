setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')

test.pub.456 <- function(conn) {
  a <- as.h2o(conn, iris)

  a[,1] <- ifelse(a[,1] == 0, 54321, 54321)
   
  testEnd()
}

doTest("Test pub 456", test.pub.456)
