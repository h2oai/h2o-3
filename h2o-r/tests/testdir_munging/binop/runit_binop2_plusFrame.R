


test.plus.onFrame <- function() {
  hex <- as.h2o( iris)

  Log.info("Try adding scalar to frame: 5 + hex")
  # if(anyEnum) expect_warning(fivePlusHex <- 5 + hex)
  # else fivePlusHex <- 5 + hex
  fivePlusHex <- 5 + hex  

  Log.info("Original frame: ")
  print(head(hex))

  Log.info("5+hex:")
  print(head(fivePlusHex))
  cat("\ndim(as.data.frame(fivePlusHex)) : ")
  cat(dim(fivePlusHex), "\n")

  Log.info("fivePlusHex - 5: ")
  fivePlusHexMinusFive <- fivePlusHex - 5

  print(head(fivePlusHexMinusFive))

  expect_that(dim(fivePlusHex), equals(dim(hex)))

  Log.info("Checking left and right: ")
  hexPlusFive <- hex + 5
  fivePlusHex <- 5 + hex

  Log.info("hex + 5: ")
  print(head(hexPlusFive))
  
  Log.info("5 + hex: ")
  print(head(fivePlusHex))

  hhpp <- data.frame(lapply(head(hexPlusFive), as.numeric) )
  hfph <- data.frame(lapply(head(fivePlusHex), as.numeric) )
 
  expect_that(hhpp, equals(hfph))

  Log.info("Try to add two frames: hex + hex")
  hd <- as.data.frame(head(hex))
  hexPlusHex <- hex + hex
  print(head(hexPlusHex))
  hdPlushd <- hd + hd
  print(head(hdPlushd))

  hd  <- data.frame(lapply(head(hdPlushd), as.numeric))
  hph <- data.frame(lapply(head(hexPlusHex), as.numeric))

  Log.info("FINAL ONE:")
  print(hd)

  Log.info("HPH:")
  print(hph)

  
}

doTest("BINOP2 EXEC2 TEST: '+' with Frames", test.plus.onFrame)

