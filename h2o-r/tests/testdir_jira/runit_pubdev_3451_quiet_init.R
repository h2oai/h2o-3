setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.quiet.init <- function() {
  
  # We call h2o.init, and see if there is any output.
  # In a normal test, h2o.init will have been called before, but the output 
  # the first and the second time this function gets called is comparable
  
  expect_silent(h2o.init(quiet = TRUE))
  
}

doTest("h2o.init with quiet option", test.quiet.init)
