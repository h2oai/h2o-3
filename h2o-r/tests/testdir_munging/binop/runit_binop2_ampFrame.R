##
# Test: binop2 & opeartor
# Description: Check the '&' binop2 operator on frames
# Variations: e1 & e2
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

test.amp.frame <- function(conn) {
  dataSet <- doSelect()
  dataName <- names(dataSet)
  dd <- dataSet[[1]]$ATTRS
  colnames <- dd$NAMES
  numCols  <- as.numeric(dd$NUMCOLS)
  numRows  <- as.numeric(dd$NUMROWS)
  colTypes <- dd$TYPES
  colRange <- dd$RANGE
  Log.info(paste("Importing ", dataName, " data..."))
  hex <- h2o.uploadFile(conn, locate(dataSet[[1]]$PATHS[1]), paste("r_", gsub('-','_',dataName),".hex", sep = ""))
  anyEnum <- FALSE
  if(any(dd$TYPES == "enum")) anyEnum <- TRUE
  
  Log.info("Expectation with frames: 5 & FRAME; FRAME & 5; FRAME & FRAME")
  Log.info("Get back a frame filled with booleans, and NAs for enums")
  Log.info("We expect to get a warning if there are enums")

  Log.info("Perform` this with a scalar first hex & 5: ")
  # if(anyEnum) expect_warning(hexAmpFive <- hex & 5)
  # else hexAmpFive <- hex & 5
  hexAmpFive <- hex & 5

  Log.info("Expect commmutativity with '&': 5 & hex")
  # if(anyEnum) expect_warning(fiveAmpHex <- 5 & hex)
  # else fiveAmpHex <- 5 & hex
  fiveAmpHex <- 5 & hex

  Log.info("Try between two frames... expect to get FALSE wherever a 0 occurs in either side of the operator")
  # if(anyEnum) expect_warning(hexAmpHex <- hex & hex)
  # else hexAmpHex <- hex & hex
  hexAmpHex <- hex & hex
  
  testEnd()
}

doTest("EXEC2 TEST: BINOP2 '&' test on frames", test.amp.frame)

