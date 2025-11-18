setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

test_relevel <- function() {
  df <- as.h2o(data.frame(
        state = c("AL", "GA", "NY", "PA"),
        bin_levels = c("$10'0", "$2\"00", "$500", "other"), stringsAsFactors=TRUE))
  print(df)
  releveled <- h2o.relevel(df[["bin_levels"]], "$500")
  expect_true(h2o.levels(releveled)[[1]] == "$500")
  releveled <- h2o.relevel(df[["bin_levels"]], "$2\"00")
  expect_true(h2o.levels(releveled)[[1]] == "$2\"00")
  releveled <- h2o.relevel(df[["bin_levels"]], "$2\\\"00")
  expect_true(h2o.levels(releveled)[[1]] == "$2\"00")
  releveled <- h2o.relevel(df[["bin_levels"]], "$10'0")
  expect_true(h2o.levels(releveled)[[1]] == "$10'0")
}

doTest("Test relevel issue with factors containing $", test_relevel)

