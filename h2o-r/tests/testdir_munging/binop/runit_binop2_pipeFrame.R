


test.pipe.frame <- function() {
  hex <- as.h2o( iris)
  
  Log.info("Expectation with frames: 5 | FRAME; FRAME | 5; FRAME | FRAME")
  Log.info("Get back a frame filled with booleans, and NAs for enums")
  Log.info("We expect to get a warning if there are enums")

  Log.info("Perform` this with a scalar first hex | 5: ")
  # if(anyEnum) expect_warning(hexPipeFive <- hex | 5)
  # else hexPipeFive <- hex | 5
  hexPipeFive <- hex | 5
  print(head(hexPipeFive))
  
  Log.info("Expect commmutativity with '|': 5 | hex")
  # if(anyEnum) expect_warning(fivePipeHex <- 5 | hex)
  # else fivePipeHex <- 5 | hex
  fivePipeHex <- 5 | hex
  print(head(fivePipeHex))
  
  Log.info("Try between two frames... expect to get TRUE no matter what (excluding enum behaviors)")
  # if(anyEnum) expect_warning(hexPipeHex <- hex | hex)
  # else hexPipeHex <- hex | hex
  hexPipeHex <- hex | hex
  print(hexPipeHex)
  
  
}

doTest("BINOP2 TEST: Exec 2 test on '|'", test.pipe.frame)

