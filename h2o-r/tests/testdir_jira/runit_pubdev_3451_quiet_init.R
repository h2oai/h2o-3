setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.quiet.init <- function() {
  
  # We don't reinitialize h2o, but instead call h2o.init again, and see if there is any output.
  
  expect_silent(h2o.init(quiet = T))
  
}

doTest("h2o.init with quiet option", test.quiet.init)