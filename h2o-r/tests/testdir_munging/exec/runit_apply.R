setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.apply <- function() {
  hex <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))

  Log.info("Perform apply on columns")

  kalls <- c("abs", "cos", "sin", "acos", "ceiling",
             "floor", "cosh", "exp", "log", "round",
             "sqrt", "tan", "tanh")

  lapply(kalls, function(call) { print(apply(hex, 2, call)); })

  print(h2o.ls())
  print(hex)

  Log.info("Now apply but reverse order of kalls")
  lapply(rev(kalls), function(call) { print(apply(hex, 2, call)); })

  print(h2o.ls())
  print(hex)

  Log.info("Now try some misc. apply calls")
  print(apply(hex, 2, function(x) { abs( x*x - x*5*x ) - 55/x; abs(x*x*x - 999/var(x[1:20,])*x ) }))

  print(h2o.ls())


  f <- function(x) { abs( x*x - x*5*x ) - 55/x; abs(x*x*x - 999/var(x[1:20,])*x ) }
  apply(hex, 2, f)
  gc()
  print(h2o.ls())

  testEnd()
}

# doesn't include issues with NAs! 
doTest("Check several cases of `apply` call ", test.apply)
