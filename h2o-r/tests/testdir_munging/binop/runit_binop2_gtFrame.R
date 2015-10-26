



test.gt.frame <- function() {
  hex <- as.h2o(iris)
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex > 5 : ")
  hexGTFive <- hex > 5 
  print(head(hexGTFive)) 

  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 > hex ")
  fiveGTHex <- 5 > hex
  print(head(fiveGTHex))

  Log.info("Try > the frame by itself: hex > hex")
  hex > hex
  print(head(hex > hex))

  
}

doTest("EXEC2 TEST: BINOP2 test of '>' on frames", test.gt.frame)

