



test.div.frame <- function() {
  hex <- as.h2o(iris)
 
  Log.info("Expectations here are to get NaN if 0/0; Inf if nonzero/0; or a number back")
  Log.info("Should get a warning message if there is an enum column and that column should be all NAs")
  
  Log.info("Try hex / 5 : ")
  # if(anyEnum) expect_warning(hexDivFive <- hex / 5)
  # else hexDivFive <- hex / 5
  hexDivFive <- hex / 5
  print(hexDivFive)
  print(head(hexDivFive))
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 / hex ")
  # if(anyEnum) expect_warning(fiveDivHex <- 5 / hex)
  # else fiveDivHex <- 5 / hex
  fiveDivHex <- 5 / hex
  print(fiveDivHex)
  print(head(fiveDivHex))
  
  Log.info("Try dividing the frame by itself: hex / hex")
  # if(anyEnum) expect_warning(hexDivHex <- hex / hex)
  # else hexDivHex <- hex / hex
  hexDivHex <- hex / hex
  print(hexDivHex)
  print(head(hexDivHex))
  
  
}

doTest("EXEC2 TEST: BINOP2 test of '/' on frames", test.div.frame)

