


test.sep <- function() {
  h2o.importFile(locate("smalldata/logreg/prostate.csv"), sep=",")
  
}

doTest("Test the separator gets mapped properly", test.sep)
