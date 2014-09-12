setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.binop2.ampersand <- function(conn) {

  hex <- as.h2o(conn, iris)

  hex & 5
  5 & hex
  5 && hex
  hex && 5
  hex && c(5,10,20)
  c(5,10,20) && hex
  hex[,1] && c(5,10,20)
  c(5,10,20) && hex[,1]
  hex[,1] & c(5,10,20)
  c(5,10,20) & hex[,1]
  
  testEnd()
}

doTest("Binop2 EQ2 Test: & and &&", test.binop2.ampersand)

