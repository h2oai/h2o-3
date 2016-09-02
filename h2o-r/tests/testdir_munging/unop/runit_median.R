setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
# Test out the h2o.median() functionality

test.median <- function() {
  Log.info("Uploading iris/iris_wheader.csv")
  iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"), "iris_wheader.hex")
  iris.dat <- read.csv(locate("smalldata/iris/iris_wheader.csv"))

  Log.info("Median of each column: ")
  for(i in 1:4) {
    iris_Median <- median(iris.dat[,i])
    iris_H2Omedian <- h2o.median(iris.hex[,i])
    Log.info(paste("Column", i, ": Median in R:", iris_Median, "\tMedian in H2O:", iris_H2Omedian))
    expect_equal(iris_Median, iris_H2Omedian)
  }


}

doTest("Test out the h2o.median() functionality", test.median)
