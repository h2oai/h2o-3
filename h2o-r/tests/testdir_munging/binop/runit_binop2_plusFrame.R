setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.plus.onH2OFrame <- function() {
  hex <- as.h2o( iris)

  h2oTest.logInfo("Try adding scalar to frame: 5 + hex")
  # if(anyEnum) expect_warning(fivePlusHex <- 5 + hex)
  # else fivePlusHex <- 5 + hex
  fivePlusHex <- 5 + hex  

  h2oTest.logInfo("Original frame: ")
  print(head(hex))

  h2oTest.logInfo("5+hex:")
  print(head(fivePlusHex))
  cat("\ndim(as.data.frame(fivePlusHex)) : ")
  cat(dim(fivePlusHex), "\n")

  h2oTest.logInfo("fivePlusHex - 5: ")
  fivePlusHexMinusFive <- fivePlusHex - 5

  print(head(fivePlusHexMinusFive))

  expect_that(dim(fivePlusHex), equals(dim(hex)))

  h2oTest.logInfo("Checking left and right: ")
  hexPlusFive <- hex + 5
  fivePlusHex <- 5 + hex

  h2oTest.logInfo("hex + 5: ")
  print(head(hexPlusFive))
  
  h2oTest.logInfo("5 + hex: ")
  print(head(fivePlusHex))

  hhpp <- data.frame(lapply(head(hexPlusFive), as.numeric) )
  hfph <- data.frame(lapply(head(fivePlusHex), as.numeric) )
 
  expect_that(hhpp, equals(hfph))

  h2oTest.logInfo("Try to add two frames: hex + hex")
  hd <- as.data.frame(head(hex))
  hexPlusHex <- hex + hex
  print(head(hexPlusHex))
  hdPlushd <- hd + hd
  print(head(hdPlushd))

  hd  <- data.frame(lapply(head(hdPlushd), as.numeric))
  hph <- data.frame(lapply(head(hexPlusHex), as.numeric))

  h2oTest.logInfo("FINAL ONE:")
  print(hd)

  h2oTest.logInfo("HPH:")
  print(hph)

  
}

h2oTest.doTest("BINOP2 EXEC2 TEST: '+' with H2OFrames", test.plus.onH2OFrame)

