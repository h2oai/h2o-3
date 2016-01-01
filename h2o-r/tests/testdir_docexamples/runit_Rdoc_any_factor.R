setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.anyFactor <- function() {

 irisPath <- h2oTest.locate("smalldata/extdata/iris_wheader.csv")
 iris.hex <- h2o.uploadFile( path = irisPath)
 h2o.anyFactor(iris.hex)


}

h2oTest.doTest("R Doc h2o.anyFactor", test.anyFactor)
