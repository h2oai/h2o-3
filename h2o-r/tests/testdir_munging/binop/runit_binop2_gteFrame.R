setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gte.frame <- function() {
 hex <- as.h2o(iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex >= 5 : ")
  hexGTEFive <- hex >= 5
  print(head(hexGTEFive))
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 >= hex ")
  fiveGTEHex <- 5 >= hex
  print(head(fiveGTEHex))
  
  Log.info("Try >= the frame by itself: hex >= hex")
  hexGTEHex <- hex >= hex
  print(hexGTEHex)

  
}

doTest("EXEC2 TEST: BINOP2 test of '>=' on frames", test.gte.frame)

