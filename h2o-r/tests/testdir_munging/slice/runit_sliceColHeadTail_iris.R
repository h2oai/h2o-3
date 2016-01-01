setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.slice.colTail <- function() {
  h2oTest.logInfo("Importing iris.csv data...\n")
  iris.hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"),destination_frame="iris.hex")
  
  iris_nrows <- nrow(iris.hex)
  iris_ncols <- ncol(iris.hex)
  
  h2oTest.logInfo("Check that iris is 150x5")
  h2oTest.logInfo(paste("Got: nrows = ", iris_nrows, sep = ""))
  h2oTest.logInfo(paste("Got: ncols = ", iris_ncols, sep = ""))
  
  expect_that(iris_nrows, equals(150))
  expect_that(iris_ncols, equals(5))
  
  h2oTest.logInfo("Check the tail of first column (sepalLength).\n Do we get an atomic vector back? (we expect to!)")
  sepalLength <- iris.hex[,1]
  
  h2oTest.logInfo("Slicing out the first column still gives an h2o object.")
  expect_that(sepalLength, is_a("H2OFrame"))
  print(head(sepalLength))
  
  h2oTest.logInfo("Tail of sepalLength is:\n")
  h2oTest.logInfo(tail(sepalLength))

  h2oTest.logInfo("Examine the first 6 elements of the hex and compare to csv data: ")
  h2oTest.logInfo("head(iris.hex[,1])[,1]")
  h2oTest.logInfo(head(iris.hex[,1])[,1])
  
  h2oTest.logInfo("head(iris[,1])")
  h2oTest.logInfo(head(iris[,1]))
  
  tryCatch(expect_that(head(iris[,1]), equals(head(iris.hex[,1])[,1])),error=function(e) e)
  
  h2oTest.logInfo("Try to slice out a single element from a column")
  
  tryCatch(iris.hex[1,1],error=function(e) print(paste("Could not perform head(iris.hex[1,1]",e)))

  
}

h2oTest.doTest("Slice Test: Tail of a column sliced out ", test.slice.colTail)

