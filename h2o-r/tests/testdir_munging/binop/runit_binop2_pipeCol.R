##
# Test: binop2 | opeartor
# Description: Check the '|' binop2 operator
# Variations: e1 | e2
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

assertEquals<-
function(p) {
    p[1] == p[2]
}

test.binop2.pipe <- function(conn) {
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

  Log.info("Selecting a column")
  #col <- sample(colnames[colTypes != "enum"], 1)
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, as.numeric(col))
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, paste("C", col+1, sep = "", collapse = ""))
  df <- head(hex)

  col <- sample(ncol(hex), 1)

  sliced <- hex[,col]
  Log.info("Placing key \"sliced.hex\" into User Store")
  sliced <- h2o.assign(sliced, "sliced.hex")
  print(h2o.ls(conn))

  Log.info("Performing the binop2 operation: 5 | col")
  Log.info("Expectation is the following: ")
  Log.info("For a non-enum column, ANDing with a single number will result in a column of booleans.")
  Log.info("TRUE is returned always")
  Log.info("This is checked on both the left and the right (which produce the same boolean vec).")

  newHex <- 5 | sliced

  expect_that(dim(newHex), equals(dim(sliced)))

  print(head(newHex))
  print(head(as.data.frame(sliced) | 5))
  
  testEnd()
}

doTest("Binop2 EQ2 Test: |", test.binop2.pipe)

