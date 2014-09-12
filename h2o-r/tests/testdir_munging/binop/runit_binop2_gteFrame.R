setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gte.frame <- function(conn) {
 hex <- as.h2o(conn, iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex >= 5 : ")
  # if(anyEnum) expect_warning(hexGTEFive <- hex >= 5)
  # else hexGTEFive <- hex >= 5
  hexGTEFive <- hex >= 5
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 >= hex ")
  # if(anyEnum) expect_warning(fiveGTEHex <- 5 >= hex)
  # else fiveGTEHex <- 5 >= hex
  fiveGTEHex <- 5 >= hex
  
  Log.info("Try >= the frame by itself: hex >= hex")
  # if(anyEnum) expect_warning(hexGTEHex <- hex >= hex)
  # else hexGTEHex <- hex >= hex
  hexGTEHex <- hex >= hex

  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '>=' on frames", test.gte.frame)

