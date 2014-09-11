##
# Test: binop2 + opeartor
# Description: Check the '+' binop2 operator
# Variations: e1 + e2
#    e1 & e2 H2OParsedData
#    e1 Numeric & e2 H2OParsedData
#    e1 H2OParsedData & e2 Numeric
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')


colCheckEquals<-
function(fr1, fr2) {
    expect_that(dim(fr1), equals(dim(fr2)))
    expect_that(fr1[,1], equals(fr2[,1]))
    for ( i in dim(fr1)[2]) {
      expect_that(fr1[,i], equals(fr2[,i]))
    }
}


#setupRandomSeed(2078846715)

doSelect<-
function() {
    d <- select()
    dd <- d[[1]]$ATTRS
    if(any(dd$TYPES != "enum")) return(d)
    Log.info("No numeric columns found in data, trying a different selection")
    doSelect()
}

test.plus.onFrame <- function(conn) {
  dataSet <- doSelect()
  dataName <- names(dataSet)
  dd <- dataSet[[1]]$ATTRS
  colnames <- dd$NAMES
  numCols  <- as.numeric(dd$NUMCOLS)
  numRows  <- as.numeric(dd$NUMROWS)
  colTypes <- dd$TYPES
  colRange <- dd$RANGE

  Log.info(paste("Importing ", dataName, " data..."))
  hex <- h2o.uploadFile(conn, locate(dataSet[[1]]$PATHS[1]), paste("r", gsub('-','_',dataName),".hex", sep = ""))
  originalDataType <- class(hex)
  hexOrig <- hex
  anyEnum <- FALSE
  if(any(dd$TYPES == "enum")) anyEnum <- TRUE

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

  testEnd()
}

doTest("BINOP2 EXEC2 TEST: '+' with Frames", test.plus.onFrame)

