##
# Test: [<- & $<-
# Description: Select a dataset, select columns, change values in the column, re-assign col
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

#setupRandomSeed(1689636624)

test.basic.slot.assignment <- function() {
  Log.info("Uploading iris data...")
  hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
  oldVal <- hex[1,1]

  Log.info("Changing the first element in the first column of iris")
  Log.info("Initial value is: ")
  Log.info(head(oldVal))

  hex[1,1] <- 48
  print(head(hex))
  hex$sepal_len <- 90  # new column
  print(head(hex))

  testEnd()
}

doTest("EQ2 Tests: [<- and $<-", test.basic.slot.assignment)

