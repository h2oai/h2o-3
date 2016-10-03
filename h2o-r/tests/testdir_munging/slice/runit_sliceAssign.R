setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.slice.assign <- function() {
  input.path <- locate("smalldata/junit/id_cols.csv")
  ids1 <- h2o.importFile(input.path, "ids1")
  ids2 <- h2o.importFile(input.path, "ids2")

  expect_equal(nrow(ids1), 120000)
  expect_equal(nrow(ids2), 120000)

  ids1.r <- as.data.frame(ids1)
  ids2.r <- as.data.frame(ids2)

  ids1.r[999:120000, 1] <- ids2.r[1:(1+120000-999), 1]
  ids1[999:120000, 1] <- ids2[1:(1+120000-999), 1]

  expect_equal(as.data.frame(ids1), ids1.r)
}

doTest("Slice Tests: Assign to a row sliced frame", test.slice.assign)