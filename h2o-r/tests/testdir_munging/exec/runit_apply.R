setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.apply <- function(conn) {
  hex <- h2o.importFile(conn, locate("smalldata/logreg/prostate.csv"))

  Log.info("Perform apply on columns")

  kalls <- c("mean", "abs", "cos", "sin", "acos", "ceiling",
             "floor", "cosh", "exp", "log", "round",
             "sqrt", "tan", "tanh")

  lapply(kalls, function(call) { print(apply(hex, 2, call)) })

  print(h2o.ls())
  print(hex)
      
  testEnd()
}

# doesn't include issues with NAs! 
doTest("Check several cases of `apply` call ", test.apply)
