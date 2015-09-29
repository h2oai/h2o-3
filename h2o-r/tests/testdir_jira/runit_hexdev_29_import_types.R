################################################################################
##
## Verifying that R can define features as categorical or continuous on import
##
################################################################################
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.continuous.or.categorical <- function() {
  df.hex <- h2o.uploadFile(locate("smalldata/jira/hexdev_29.csv"),
    col.types = c("enum", "enum", "enum"))

  expect_true(is.factor(df.hex$h1))
  expect_true(is.factor(df.hex$h2))
  expect_true(is.factor(df.hex$h3))

  
}

doTest("Veryfying R Can Declare Types on Import", test.continuous.or.categorical)
