setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.merge_no_shared_cols <- function() {

  left <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'blueberry'),
    color = c('red', 'orange', 'yellow', 'yellow', 'red', 'blue'))
  rite <- data.frame(name  = c('Cliff','Arno','Tomas','Michael'),
    skill = c('hacker','science','linearmath','sparkling'))

  l.hex <- as.h2o(left)
  r.hex <- as.h2o(rite)

  Log.info("H2O will only merge if data sets have at least one shared column")
  expect_error(h2o.merge(l.hex, r.hex, all=TRUE))
  expect_error(h2o.merge(l.hex, r.hex, TRUE)) # test TRUE incorrectly passed to by=
  expect_error(h2o.merge(l.hex, r.hex))
}

doTest("Datasets Require Shared Columns to Merge", check.merge_no_shared_cols)


