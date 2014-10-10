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

  hex <- as.h2o(conn, iris)

  colsToSelect <- 1 #sample(ncol(hex), 1)

  col <- sample(ncol(hex), colsToSelect)

  numToReplace <- sample(nrow(hex),1)
  rowsToReplace <- sample(nrow(hex), numToReplace)

  hexOriginal <- data.frame(col = as.data.frame(hex)[rowsToReplace,col])
  #Log.info(paste("Original Column: ", col, sep = ""))
  print(head(hexOriginal))

  replacement <- rnorm(rowsToReplace)
  Log.info("Replacing rows for column selected")

  replacement <- as.h2o(conn, replacement)

  print(rowsToReplace)
  print(paste("Column selected: ", col))
  print(paste("Column - 1: ", col - 1))


  hex[rowsToReplace,col] <- replacement

  head(hex)

  hexReplaced <- data.frame(col = as.data.frame(hex)[rowsToReplace,col]) 


  print(hex)

  print(hexReplaced)

  #expect_false(hexReplaced, equals(hexOriginal))

  testEnd()

}

doTest("EQ2 Tests: [<-", test.column.assignment)

