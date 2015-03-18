setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.summary.numeric <- function(conn) {
  Log.info("Importing USArrests.csv data...\n")
  # arrests.hex <- h2o.importFile(conn, locate("smalldata/pca_test/USArrests.csv", schema = "local"), "arrests.hex")
  arrests.hex <- as.h2o(conn, USArrests, key = "arrests.hex")

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

  Log.info("Convert summaries to numeric lists")
  summary_to_numeric <- function(s) {
    lapply(s, function(x) {
      val <- strsplit(x, ":")[[1]][2]
      as.numeric(val)
    })
  }

  Log.info("Compare R and H2O, accounting for binning error")
  valR <- unlist(summary_to_numeric(summary_2))
  valH2O <- unlist(summary_to_numeric(summary(tail(arrests.hex))))
  for (i in 1:6)
    expect_equal(valH2O, valR, 1)
  for (i in 7:12)
    expect_equal(valH2O, valR, 7)
  for (i in 13:18)
    expect_equal(valH2O, valR, 7)
  for (i in 19:24)
    expect_equal(valH2O, valR, 2.5)

  testEnd()
}

doTest("Summary Tests", test.summary.numeric)

