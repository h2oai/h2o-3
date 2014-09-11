setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.summary.numeric <- function(conn) {
  Log.info("Importing USArrests.csv data...\n")
  # arrests.hex <- h2o.importFile(conn, locate("smalldata/pca_test/USArrests.csv", schema = "local"), "arrests.hex")
  arrests.hex = h2o.importFile(conn, normalizePath(locate("smalldata/pca_test/USArrests.csv")), "arrests.hex")  

  Log.info("Check that summary works...")
  summary(arrests.hex)
  
  summary_ <- summary(arrests.hex)
  
  Log.info("Check that we get a table back from the summary(hex)")
  expect_that(summary_, is_a("table"))
  
  summary_2 <- summary(tail(USArrests))

  Log.info("Check that the summary of the tail of the dataset is the same as what R produces: ")
  Log.info("summary(tail(USArrests))\n")
  
  print(summary_2)
  Log.info("summary(tail(arrests.hex))\n")
  
  print(summary(tail(arrests.hex)))
  expect_that(summary(tail(arrests.hex)), equals(summary_2))

  testEnd()
}

doTest("Summary Tests", test.summary.numeric)

