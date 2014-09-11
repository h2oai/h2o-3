##
# Test: binop2 & opeartor
# Description: Check the '&' binop2 operator
# Variations: e1 & e2
#    e1 & e2 H2OParsedData
#    e1 Numeric & e2 H2OParsedData
#    e1 H2OParsedData & e2 Numeric
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

setupRandomSeed(1519946107)
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

test.binop2.ampersand <- function(conn) {
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

  Log.info("Selecting a column")
  #col <- sample(colnames[colTypes != "enum"], 1)
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, as.numeric(col) + 1)
  #col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, paste("C", col, sep = "", collapse = ""))
  df <- head(hex)
  print(df)
  col <- sample(colnames(df[!sapply(df, is.factor)]), 1)
  #if (!(grepl("\\.", col))) {
  #  col <- gsub("\\.", " ", sample(colnames(df[!sapply(df, is.factor)]), 1)) 
  #}
    print(which(col == colnames(df)))

  print(colnames(hex))
  print(col)

  print(col %in% colnames(hex))
  print(col %in% colnames(df))

  if (!(col %in% colnames(hex))) {
    col <- which(col == colnames(df))
  }
  Log.info(paste("Using column: ", col))
 
  sliced <- hex[,col] #expect this to be numeric!
  Log.info("Placing key \"sliced.hex\" into User Store")
  sliced <- h2o.assign(sliced, "sliced.hex")
  print(h2o.ls(conn))

  Log.info("Performing the binop2 operation: 5 & col")
  Log.info("Expectation is the following: ")
  Log.info("For a non-enum column, ANDing with a single number will result in a column of booleans.")
  Log.info("TRUE is returned if neither digit being ANDed was a 0, FALSE otherwise.")
  Log.info("This is checked on both the left and the right (which produce the same boolean vec).")

  newHex <- 5 & sliced

  expect_that(length(as.data.frame(newHex)), equals(length(as.data.frame(sliced))))
  
  Log.info("length(as.data.frame(newHex)): ")
  print(length(as.data.frame(newHex)))
  
  Log.info("dim(data.frame(as.data.frame(sliced) & 5)): ")
  print(dim(data.frame(as.data.frame(sliced))))
   
  expect_that(dim(data.frame(as.data.frame(newHex))), equals(dim(data.frame(as.data.frame(sliced) & 5))))
  Log.info("ANDed hex (should be a column of 1s & 0s)")
  print(head(newHex))
  Log.info("Expected result: as.data.frame(sliced) & 5")
  print(head(as.data.frame(sliced) & 5))

  df <- data.frame(h2o = as.data.frame(newHex), R = data.frame(as.data.frame(sliced) & 5))
  df <- na.omit(df)
  Log.info("nrow(df)")
  print(nrow(df))
  Log.info("sum(apply(df,1,assertEquals))")
  print(sum(apply(df,1,assertEquals)))
  expect_that(sum(apply(df,1,assertEquals)), equals(nrow(df)))
  
  testEnd()
}

doTest("Binop2 EQ2 Test: &", test.binop2.ampersand)

