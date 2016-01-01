setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
setwd(normalizePath(dirname(R.utils::commandArgs(asValues= TRUE)$"f")))


check.merge_col_types <- function() {
  left <- data.frame(fruit = c('apple', 'orange', 'banana', 'lemon', 'strawberry', 'blueberry'),
                     color = c('red', 'orange', 'yellow', 'yellow', 'red', 'blue'))
  rite <- data.frame(fruit = c(1, 5, 2, 4, 6,3),
                     citrus = c(F, T, F, T, F, F))

  l.hex <- as.h2o(left)
  r.hex <- as.h2o(rite)

  expect_error(h2o.merge(l.hex, r.hex, T))

  
}

h2oTest.doTest("Matching Column Names Must Have Same Data Types", check.merge_col_types)
