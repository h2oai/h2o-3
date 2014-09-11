##
# Test: binop2 | opeartor
# Description: Check the '|' binop2 operator on frames
# Variations: e1 | e2
#    e1 & e2 H2OParsedData
#    e1 Numeric & e2 H2OParsedData
#    e1 H2OParsedData & e2 Numeric
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

#setupRandomSeed(339601269)

doSelect<-
function() {
    d <- select()
    dd <- d[[1]]$ATTRS
    if(any(dd$TYPES != "enum")) return(d)
    Log.info("No numeric columns found in data, trying a different selection")
    doSelect()
}

test.pipe.frame <- function(conn) {
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
  anyEnum <- FALSE
  if(any(dd$TYPES == "enum")) anyEnum <- TRUE
  
  Log.info("Expectation with frames: 5 | FRAME; FRAME | 5; FRAME | FRAME")
  Log.info("Get back a frame filled with booleans, and NAs for enums")
  Log.info("We expect to get a warning if there are enums")

  Log.info("Perform` this with a scalar first hex | 5: ")
  # if(anyEnum) expect_warning(hexPipeFive <- hex | 5)
  # else hexPipeFive <- hex | 5
  hexPipeFive <- hex | 5
  
  Log.info("Expect commmutativity with '|': 5 | hex")
  # if(anyEnum) expect_warning(fivePipeHex <- 5 | hex)
  # else fivePipeHex <- 5 | hex
  fivePipeHex <- 5 | hex
  
  Log.info("Try between two frames... expect to get TRUE no matter what (excluding enum behaviors)")
  # if(anyEnum) expect_warning(hexPipeHex <- hex | hex)
  # else hexPipeHex <- hex | hex
  hexPipeHex <- hex | hex
  
  testEnd()
}

doTest("BINOP2 TEST: Exec 2 test on '|'", test.pipe.frame)

