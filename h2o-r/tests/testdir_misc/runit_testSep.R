setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.sep <- function() {
  h2o.importFile(locate("smalldata/logreg/prostate.csv"), sep=",")
  testEnd()
}

doTest("Test the separator gets mapped properly", test.sep)
