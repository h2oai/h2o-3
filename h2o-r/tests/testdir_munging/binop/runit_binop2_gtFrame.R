setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test.gt.frame <- function(conn) {
  hex <- as.h2o(conn, iris)
  anyEnum <- FALSE
  if(any(dd$TYPES == "enum")) anyEnum <- TRUE
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex > 5 : ")
  # if(anyEnum) expect_warning(hexGTFive <- hex > 5)
  # else hexGTFive <- hex > 5
  hexGTFive <- hex > 5  

  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 > hex ")
  # if(anyEnum) expect_warning(fiveGTHex <- 5 > hex)
  # else fiveGTHex <- 5 > hex
  fiveGTHex <- 5 > hex  

  Log.info("Try > the frame by itself: hex > hex")
  # if(anyEnum) expect_warning(hexGTHex <- hex > hex)
  # else hexGTHex <- hex > hex
  hex > hex

  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '>' on frames", test.gt.frame)

