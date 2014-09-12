setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.summary2 <- function(conn) {
  Log.info("Importing iris.csv data...\n")
  # iris.hex <- h2o.importFile(conn, locate("smalldata/iris/iris_wheader.csv", schema="local"))
  iris.hex <- h2o.importFile(conn, normalizePath(locate("smalldata/iris/iris_wheader.csv")))  

 Log.info("Check that summary works...")
 print(summary(iris.hex)) 

 Log.info("Summary from R's iris data: ")
 summary(iris)

  testEnd()
}

doTest("Summary2 Test", test.summary2)

