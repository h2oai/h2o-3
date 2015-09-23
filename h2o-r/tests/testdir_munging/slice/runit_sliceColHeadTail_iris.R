setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.slice.colTail <- function() {
  Log.info("Importing iris.csv data...\n")
  iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"),destination_frame="iris.hex")
  
  iris_nrows <- nrow(iris.hex)
  iris_ncols <- ncol(iris.hex)
  
  Log.info("Check that iris is 150x5")
  Log.info(paste("Got: nrows = ", iris_nrows, sep = ""))
  Log.info(paste("Got: ncols = ", iris_ncols, sep = ""))
  
  expect_that(iris_nrows, equals(150))
  expect_that(iris_ncols, equals(5))
  
  Log.info("Check the tail of first column (sepalLength).\n Do we get an atomic vector back? (we expect to!)")
  sepalLength <- iris.hex[,1]
  
  Log.info("Slicing out the first column still gives an h2o object.")
  expect_that(sepalLength, is_a("Frame"))
  print(head(sepalLength))
  
  Log.info("Tail of sepalLength is:\n")
  Log.info(tail(sepalLength))

  Log.info("Examine the first 6 elements of the hex and compare to csv data: ")
  Log.info("head(iris.hex[,1])[,1]")
  Log.info(head(iris.hex[,1])[,1])
  
  Log.info("head(iris[,1])")
  Log.info(head(iris[,1]))
  
  tryCatch(expect_that(head(iris[,1]), equals(head(iris.hex[,1])[,1])),error=function(e) e)
  
  Log.info("Try to slice out a single element from a column")
  
  tryCatch(iris.hex[1,1],error=function(e) print(paste("Could not perform head(iris.hex[1,1]",e)))

  testEnd()
}

doTest("Slice Test: Tail of a column sliced out ", test.slice.colTail)

