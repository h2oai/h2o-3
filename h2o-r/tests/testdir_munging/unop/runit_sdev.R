setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Test out the var() functionality
# If H2O dataset x, get back square data frame with dimension ncol(x)
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##




test.sdev <- function() {
  h2oTest.logInfo("Uploading iris/iris_wheader.csv")
  iris.hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"), "iris_wheader.hex")
  iris.dat <- read.csv(h2oTest.locate("smalldata/iris/iris_wheader.csv"))
  
  h2oTest.logInfo("Standard deviation of each column: ")
  for(i in 1:4) {
    iris_Rsd <- sd(iris.dat[,i])
    iris_H2Osd <- sd(iris.hex[,i])
    h2oTest.logInfo(paste("Column", i, ": sd in R:", iris_Rsd, "\tsd in H2O:", iris_H2Osd))
    expect_equal(iris_Rsd, iris_H2Osd)
  }
  
  
}

h2oTest.doTest("Test out the sd() functionality", test.sdev)
