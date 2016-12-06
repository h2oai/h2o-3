setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_2844 <- function() {
  df <- iris
  hf <- as.h2o(df, destination_frame = "pubdev2844")
  expect_true(is.h2o(hf)) # dummy test
}

doTest("PUBDEV-284", test.pubdev_2844)
