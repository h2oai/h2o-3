setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test: [<- & $<-
# Description: Select a dataset, select columns, change values in the column, re-assign col
##




#setupRandomSeed(1689636624)

test.basic.slot.assignment <- function() {
  h2oTest.logInfo("Uploading iris data...")
  hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"), "iris.hex")
  oldVal <- hex[1,1]

  h2oTest.logInfo("Changing the first element in the first column of iris")
  h2oTest.logInfo("Initial value is: ")
  h2oTest.logInfo(head(oldVal))

  hex[1,1] <- 48
  print(head(hex))
  hex$sepal_len <- 90  # new column
  print(head(hex))

  
}

h2oTest.doTest("EQ2 Tests: [<- and $<-", test.basic.slot.assignment)

