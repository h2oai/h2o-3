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

#setupRandomSeed(42)

doSelect<-
function() {
    d <- select()
    dd <- d[[1]]$ATTRS
    if(any(dd$TYPES != "enum")) return(d)
    Log.info("No numeric columns found in data, trying a different selection")
    doSelect()
}

colPlus.numeric <- function(conn) {
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

   

  Log.info("Try adding scalar to a numeric column: 5 + hex[,col]")
  #col <- sample(colnames[colTypes != "enum"], 1)
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, as.numeric(col) + 1)
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, paste("C", col, sep = "", collapse = ""))
  df <- head(hex)
  col <- sample(colnames(df[!sapply(df, is.factor)]), 1)
  if (!(grepl("\\.", col))) {
    col <- gsub("\\.", " ", sample(colnames(df[!sapply(df, is.factor)]), 1)) 
  }
  
  print(which(col == colnames(df)))

  print(colnames(hex))
  print(col)

  print(col %in% colnames(hex))
  print(col %in% colnames(df))

  if (!(col %in% colnames(hex))) {
    col <- which(col == colnames(df))
  }
  
  Log.info(paste("Using column: ", col))
 
  sliced <- hex[,col]
  Log.info("Placing key \"sliced.hex\" into User Store")
  sliced <- h2o.assign(sliced, "sliced.hex")
  print(h2o.ls(conn))

  Log.info("Adding 5 to sliced.hex")
  slicedPlusFive <- sliced + 5
  slicedPlusFive <- h2o.assign(slicedPlusFive, "slicedPlusFive.hex")

  Log.info("Orignal sliced: ")
  print(head(as.data.frame(sliced)))

  Log.info("Sliced + 5: ")
  print(head(as.data.frame(slicedPlusFive)))
  expect_that(as.data.frame(slicedPlusFive), equals(5 + as.data.frame(sliced)))

  Log.info("Checking left and right: ")
  slicedPlusFive <- sliced + 5

  fivePlusSliced <- 5 + sliced

  Log.info("sliced + 5: ")
  print(head(slicedPlusFive))

  Log.info("5 + sliced: ")
  print(head(fivePlusSliced))
  expect_that(as.data.frame(slicedPlusFive), equals(as.data.frame(fivePlusSliced)))


  Log.info("Checking the variation of H2OParsedData + H2OParsedData")

  hexPlusHex <- fivePlusSliced + slicedPlusFive

  Log.info("FivePlusSliced + slicedPlusFive: ")
  print(head(hexPlusHex))
  expect_that(as.data.frame(hexPlusHex), equals(2*as.data.frame(fivePlusSliced)))

  testEnd()
}

doTest("Column Addition With Scaler", colPlus.numeric)

