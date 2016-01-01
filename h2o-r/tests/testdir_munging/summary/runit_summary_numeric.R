setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.summary.numeric <- function() {
  h2oTest.logInfo("Importing USArrests.csv data...\n")
  # arrests.hex <- h2o.importFile(h2oTest.locate("smalldata/pca_test/USArrests.csv", schema = "local"), "arrests.hex")
  arrests.hex <- as.h2o(USArrests, destination_frame = "arrests.hex")

  h2oTest.logInfo("Check that summary works...")
  summary(arrests.hex)

  summary_ <- summary(arrests.hex)

  h2oTest.logInfo("Check that we get a table back from the summary(hex)")
  expect_that(summary_, is_a("table"))

  summary_2 <- summary(tail(USArrests))

  h2oTest.logInfo("Check that the summary of the tail of the dataset is the same as what R produces: ")
  h2oTest.logInfo("summary(tail(USArrests))\n")

  print(summary_2)
  h2oTest.logInfo("summary(tail(arrests.hex))\n")

  print(summary(tail(arrests.hex)))
  # large tolerance because median uses the rollup summary stats, which give
  # quantiles accurate to 1 part in 1000 only.
  h2oTest.checkSummary(summary(tail(arrests.hex)), summary_2, tolerance = 2e-3)

  
}

h2oTest.doTest("Summary Tests", test.summary.numeric)

