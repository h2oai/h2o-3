setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_settimezone.golden <- function(H2Oserver) {
  origTZ = h2o.getTimezone(H2Oserver)
  h2o.setTimezone("Etc/UTC", H2Oserver)
  h2o.setTimezone(as.character(origTZ[1,1]), H2Oserver)
  testEnd()
}

doTest("R Doc setTimezone", test.rdoc_settimezone.golden)
