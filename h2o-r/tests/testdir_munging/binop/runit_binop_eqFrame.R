setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gte.frame <- function(conn) {
 hex <- as.h2o(conn, iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex == 'setosa' : ")
  hexGTEFive <- hex == "setosa"
  print(head(hexGTEFive))
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 'setosa' == hex ")
  fiveGTEHex <- "setosa" == hex
  print(head(fiveGTEHex))
  
  Log.info("Try >= the frame by itself: hex == hex")
  hexGTEHex <- hex == hex
  print(hexGTEHex)

  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '>=' on frames", test.gte.frame)

