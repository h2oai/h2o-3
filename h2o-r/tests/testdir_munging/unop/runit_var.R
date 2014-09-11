##
# Test out the var() functionality
# If H2O dataset x, get back square data frame with dimension ncol(x)
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.var <- function(conn) {
  Log.info("Uploading swiss_clean.csv")
  swiss.hex <- h2o.uploadFile(conn, locate("smalldata/swiss_clean.csv"), "swiss.hex")
  swiss.dat <- read.csv(locate("smalldata/swiss_clean.csv"))
  
  Log.info("The variance of swiss.csv when read into R is: ")
  print(var(swiss.dat))
  
  Log.info("The variance of swiss.csv when asking H2O: ")
  swiss_H2Ovar <- as.matrix(var(swiss.hex))
  print(swiss_H2Ovar)
  
  expect_equal(dim(swiss_H2Ovar), c(ncol(swiss.hex), ncol(swiss.hex)))
  # Note: expect_equivalent ignores attributes of objects. We need this since H2O doesn't support rownames.
  expect_equivalent(swiss_H2Ovar, var(swiss.dat))  
  
  Log.info("Uploading iris_wheader.csv")
  iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris_wheader.csv"), "iris_wheader.hex")
  iris.dat <- read.csv(locate("smalldata/iris/iris_wheader.csv"))
  
  # Column 5 of iris is categorical, so should reject
  expect_error(var(iris.hex))
  
  Log.info("Slice out iris[,1] and get the variance: ")
  Log.info(paste("R:", var(iris.dat[,1]), "\tH2O:", var(iris.hex[,1])))
  expect_equal(var(iris.hex[,1]), var(iris.dat[,1]))
  
  Log.info("Slice iris[,1:4] and get the variance: ")
  Log.info("The variance of iris[,1:4] when read into R is: ")
  iris_Rvar <- var(iris.dat[,1:4])
  print(iris_Rvar)
  
  Log.info("The variance of iris[,1:4] when asking H2O is: ")
  iris_H2Ovar <- as.matrix(var(iris.hex[,1:4]))
  expect_equivalent(iris_H2Ovar, var(iris.dat[,1:4]))
  testEnd()
}

doTest("Test out the var() functionality", test.var)