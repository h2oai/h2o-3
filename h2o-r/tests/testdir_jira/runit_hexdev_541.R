setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.hex.541 <- function() {
  d <- c(0, 1174, NaN, NaN, 330, NaN)
  df <- as.h2o(d)

  print(max(df))
  print(min(df))
}

doTest("max/min/mean/sd/nacnt should all be na.rm=TRUE by default", test.hex.541)
