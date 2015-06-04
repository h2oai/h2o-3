setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_settimezone.golden <- function(H2Oserver) {
  h2o.setTimezone("Etc/UTC", H2OServer)
  testEnd()
}

doTest("R Doc setTimezone", test.rdoc_settimezone.golden)