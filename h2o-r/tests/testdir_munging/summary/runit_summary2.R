


test.summary2 <- function() {
  Log.info("Importing iris.csv data...\n")
  # iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv", schema="local"))
  iris.hex <- h2o.importFile(normalizePath(locate("smalldata/iris/iris_wheader.csv")))  

  Log.info("Check that summary works...")
  print(summary(iris.hex)) 

  Log.info("Summary from R's iris data: ")
  summary(iris)
  
}

doTest("Summary2 Test", test.summary2)

