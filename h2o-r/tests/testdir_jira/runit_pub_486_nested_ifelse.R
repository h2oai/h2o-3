setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.nested_ifelse <- function() {
  a <- as.h2o(2)
  b <- as.h2o(2)
  d <- as.h2o(2)
  e <- ifelse(a == b, b, ifelse(b == b, ifelse(a == d, 3.1415, 0), a))
  print(e)

  r.hex <- as.h2o(iris[,1:4])
  f <- ifelse( a == 1, !c(3), 1.23)
  g <- ifelse( b == 0, !c(!3), 1.23 <= 2.34)

  f <- ifelse( a == 1, !c(1), 1.23)
  g <- ifelse( b == 0, !c(!3), 1.23 <= 2.34)

  
}

h2oTest.doTest("Test frame add.", test.nested_ifelse )
