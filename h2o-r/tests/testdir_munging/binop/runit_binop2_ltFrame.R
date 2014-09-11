##
# Test: binop2 < opeartor
# Description: Check the '<' binop2 operator
# Variations: e1 < e2; e2 < e1
#    e1 & e2 H2OParsedData
#    e1 Numeric & e2 H2OParsedData
#    e1 H2OParsedData & e2 Numeric
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

#setupRandomSeed(42)

doSelect<-
function() {
    d <- select()
    dd <- d[[1]]$ATTRS
    if(any(dd$TYPES != "enum")) return(d)
    Log.info("No numeric columns found in data, trying a different selection")
    doSelect()
}

test.lt.frame <- function(conn) {
  dataSet <- doSelect()
  dataName <- names(dataSet)
  dd <- dataSet[[1]]$ATTRS
  colnames <- dd$NAMES
  numCols  <- as.numeric(dd$NUMCOLS)
  numRows  <- as.numeric(dd$NUMROWS)
  colTypes <- dd$TYPES
  colRange <- dd$RANGE
  Log.info(paste("Importing ", dataName, " data..."))
  hex <- h2o.uploadFile(conn, locate(dataSet[[1]]$PATHS[1]), paste("r",gsub('-','_',dataName),".hex", sep = ""))
  anyEnum <- FALSE
  if(any(dd$TYPES == "enum")) anyEnum <- TRUE
 
  Log.info("Expectation is a frame of booleans")
  
  Log.info("Try hex < 5 : ")
  # if(anyEnum) expect_warning(hexLTFive <- hex < 5)
  # else hexLTFive <- hex < 5
  hexLTFive <- hex < 5
  
  Log.info("Don't expect commutativity, but expect operation to work when operands switched: 5 < hex ")
  # if(anyEnum) expect_warning(fiveLTHex <- 5 < hex)
  # else fiveLTHex <- 5 < hex
  fiveLTHex <- 5 < hex
  
  Log.info("Try < the frame by itself: hex < hex")
  # if(anyEnum) expect_warning(hexLTHex <- hex < hex)
  # else hexLTHex <- hex < hex
  hexLTHex <- hex < hex
  
  testEnd()
}

doTest("EXEC2 TEST: BINOP2 test of '<' on frames", test.lt.frame)

