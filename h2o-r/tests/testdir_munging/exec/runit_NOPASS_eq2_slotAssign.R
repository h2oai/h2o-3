##
# Test: [<-
# Description: Select a dataset, select columns, change values in the column, re-assign col
# Variations: Single col, multi-col, factor col
# Author: Spencer
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

#setupRandomSeed(1689636624)

test.column.assignment <- function(conn) {
  dataSet <- select()
  dataName <- names(dataSet)
  dd <- dataSet[[1]]$ATTRS
  Log.info(paste("Importing ", dataName, " data..."))
  hex <- h2o.importFile(conn, locate(dataSet[[1]]$PATHS[1]), paste("r", gsub('-','_',dataName),".hex", sep = ""))

  #could replace these with h2o-R call, but not testing that here
  colnames <- dd$NAMES
  numCols  <- as.numeric(dd$NUMCOLS)
  numRows  <- as.numeric(dd$NUMROWS)
  colTypes <- dd$TYPES
  colRange <- dd$RANGE
  Log.info(numRows)

  Log.info("Select 1 column, change some values, re-assign with [<-")
  col <- sample(colnames, 1)
  col <- ifelse(is.na(suppressWarnings(as.numeric(col))), col, paste("C", col, sep = "", collapse = ""))
  Log.info(paste("Using column: ", col))
  type <- colTypes[colnames == col]

  numToReplace <- sample(numRows,1)
  rowsToReplace <- sample(numRows, numToReplace)

  hexOriginal <- data.frame(col = as.data.frame(hex)[rowsToReplace,col])
  Log.info(paste("Original Column: ", col, sep = ""))
  print(head(hexOriginal))

  replacement <- ifelse(type == "enum", sapply(rowsToReplace, genString), rnorm(rowsToReplace))
  Log.info("Replacing rows for column selected")

  hex[rowsToReplace,col] <- replacement

  hexReplaced <- data.frame(col = as.data.frame(hex)[rowsToReplace,col]) 
  expect_false(hexReplaced, equals(hexOriginal))

  testEnd()

}

doTest("EQ2 Tests: [<-", test.column.assignment)

