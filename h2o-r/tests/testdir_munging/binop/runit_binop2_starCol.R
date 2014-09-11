##
# Test: binop2 * opeartor
# Description: Check the '*' binop2 operator
# Variations: e1 * e2
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

toDouble <- function(r) ifelse(is.integer(r), as.numeric(r), r)

test.slice.star <- function(conn) {
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

  Log.info("Try adding scalar to a numeric column: 5 * hex[,col]")

  col <- sample(ncol(hex), 1)

  Log.info(paste("Using column: ", col))
 
  sliced <- hex[,col]
  Log.info("Placing key \"sliced.hex\" into User Store")
  sliced <- h2o.assign(sliced, "sliced.hex")

  Log.info("*ing 5 to sliced.hex")
  slicedStarFive <- sliced * 5
  slicedStarFive <- h2o.assign(slicedStarFive, "slicedStarFive.hex")

  Log.info("Checking left and right: ")
  slicedStarFive <- sliced * 5
  fiveStarSliced <- 5 * sliced

  Log.info("sliced * 5: ")
  print(head(slicedStarFive))

  Log.info("5 * sliced: ")
  print(head(fiveStarSliced))

  Log.info("Checking the variation of H2OParsedData * H2OParsedData")

  hexStarHex <- fiveStarSliced * slicedStarFive

  Log.info("FiveStarSliced * slicedStarFive: ")
  print(head(hexStarHex))
 
  Log.info("as.data.frame(fiveStarSliced) * as.data.frame(fiveStarSliced)")

  testEnd()
}

doTest("BINOP2 EXEC2 TEST: *", test.slice.star)

