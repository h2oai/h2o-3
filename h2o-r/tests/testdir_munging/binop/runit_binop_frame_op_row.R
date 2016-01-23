setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.slice.div <- function() {
  hex <- as.h2o(iris)
  diff <- hex[,1:4] - hex[1,1:4]
  print(diff)
}

doTest("test op between a single row and a frame", test.slice.div)

