setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test: [<-
# Description: Select a dataset, select columns, change values in the column, re-assign col
# Variations: Single col, multi-col, factor col
# Author: Spencer
##




#setupRandomSeed(1689636624)

test.column.assignment <- function() {
  set.seed(1841604082)
  hex <- as.h2o(iris)

  colsToSelect <- 1 #sample(ncol(hex), 1)

  col <- sample(ncol(hex), colsToSelect)

  numToReplace <- sample(nrow(hex),1)
  rowsToReplace <- sample(nrow(hex), numToReplace)

  print("")
  print("")
  print("Rows to replace: ")
  print(rowsToReplace)
  print("Num to replace: ")
  print(numToReplace)
  print("")
  print("")

  hexOriginal <- data.frame(col = as.data.frame(hex)[rowsToReplace,col])
  #h2oTest.logInfo(paste("Original Column: ", col, sep = ""))
  print(head(hexOriginal))

  replacement <- rnorm(numToReplace)
  h2oTest.logInfo("Replacing rows for column selected")

  replacement <- as.h2o(replacement)

  print("Wuz replacement one? ")
  print("")
  print(replacement)
  print("")
  print("")

  print(rowsToReplace)
  print(paste("Column selected: ", col))
  print(paste("Column - 1: ", col - 1))


  hex[rowsToReplace,col] <- replacement

  hexReplaced <- data.frame(col = as.data.frame(hex)[rowsToReplace,col]) 

  print(hexReplaced)

  expect_false(all(hexReplaced==hexOriginal))

  

}

h2oTest.doTest("EQ2 Tests: [<-", test.column.assignment)

