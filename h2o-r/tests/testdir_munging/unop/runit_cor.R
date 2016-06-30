setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the cor() functionality
# If H2O dataset x, get back cor data frame with dimension ncol(x)
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##




test.cor <- function() {
  hex <- as.h2o(iris)

  Log.info("Slice out iris[,1] and get the correlation: ")
  Log.info(paste("R:", cor(as.matrix(iris[,1])), "\tH2O:", cor(hex[,1])))
  cor_R = cor(as.matrix(iris[,1]))
  cor_h2o = cor(hex[,1])
  expect_equal(cor_h2o, cor_R[1,1])

  Log.info("Slice iris[,1:4] and get the correlation: ")
  Log.info("The correlation of iris[,1:4] when read into R is: ")
  iris_Rcor <- cor(iris[,1:4])
  print(iris_Rcor)

  Log.info("The correlation of iris[,1:4] when asking H2O is: ")
  iris_H2Ocor <- as.data.frame(cor(hex[,1:4]))

  print(iris_H2Ocor)


  h2o_vec <- as.vector(unlist(iris_H2Ocor))
  r_vec   <- as.vector(unlist(iris_Rcor))

  expect_equal(h2o_vec, r_vec, tol=1e-6)

  for (i in c(1e1,1e2,1e3,1e4,1e5,1e6,1e7,1e8,1e9)) {
    expect_equal(h2o.cor(as.h2o(c(i,1+i,10+i))), 1);
  }


}

doTest("Test out the cor() functionality", test.cor)