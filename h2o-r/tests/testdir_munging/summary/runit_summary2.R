setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.summary2 <- function() {
  h2oTest.logInfo("Importing iris.csv data...\n")
  # iris.hex <- h2o.importFile(h2oTest.locate("smalldata/iris/iris_wheader.csv", schema="local"))
  iris.hex <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/iris/iris_wheader.csv")))  

  h2oTest.logInfo("Check that summary works...")
  print(summary(iris.hex)) 

  h2oTest.logInfo("Summary from R's iris data: ")
  summary(iris)
  
}

h2oTest.doTest("Summary2 Test", test.summary2)

