setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the var() functionality
# If H2O dataset x, get back square data frame with dimension ncol(x)
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##




test.var <- function() {
  hex <- as.h2o(iris)  

  Log.info("Slice out iris[,1] and get the variance: ")
  Log.info(paste("R:", var(iris[,1]), "\tH2O:", var(hex[,1])))
  expect_equal(var(hex[,1]), var(iris[,1]))
  
  Log.info("Slice iris[,1:4] and get the variance: ")
  Log.info("The variance of iris[,1:4] when read into R is: ")
  iris_Rvar <- var(iris[,1:4])
  print(iris_Rvar)
  
  Log.info("The variance of iris[,1:4] when asking H2O is: ")
  iris_H2Ovar <- as.data.frame(var(hex[,1:4]))

  print(iris_H2Ovar)


  h2o_vec <- as.vector(unlist(iris_H2Ovar))
  r_vec   <- as.vector(unlist(iris_Rvar))

  expect_equal(h2o_vec, r_vec, tol=1e-6)

  for (i in c(1e1,1e2,1e3,1e4,1e5,1e6,1e7,1e8,1e9)) { 
    expect_equal(h2o.var(as.h2o(c(i,1+i,10+i))), 30 + 1/3);
  }

  
}

doTest("Test out the var() functionality", test.var)
