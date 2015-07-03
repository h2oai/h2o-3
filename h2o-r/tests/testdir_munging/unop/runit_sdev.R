##
# Test out the var() functionality
# If H2O dataset x, get back square data frame with dimension ncol(x)
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.sdev <- function() {
  Log.info("Uploading iris/iris_wheader.csv")
  iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"), "iris_wheader.hex")
  iris.dat <- read.csv(locate("smalldata/iris/iris_wheader.csv"))
  
  Log.info("Standard deviation of each column: ")
  for(i in 1:4) {
    iris_Rsd <- sd(iris.dat[,i])
    iris_H2Osd <- sd(iris.hex[,i])
    Log.info(paste("Column", i, ":", "sd in R:", iris_Rsd, "\tsd in H2O:", iris_H2Osd))
    expect_equal(iris_Rsd, iris_H2Osd)
  }
  
  expect_error(sd(iris.hex[,5]))   # Error if column is categorical
  expect_error(sd(iris.hex[,1:2]))   # Error if more than one column
  
  testEnd()
}

doTest("Test out the sd() functionality", test.sdev)
