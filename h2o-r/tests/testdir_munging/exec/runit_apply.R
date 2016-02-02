setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.apply <- function() {
  hex <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))

  Log.info("Perform apply on columns")

  # Apply on some primitives
  kalls <- c("abs", "cos", "sin", "acos", "ceiling",
             "floor", "cosh", "exp", "log",
             "sqrt", "tan", "tanh")

  lapply(kalls, function(call) { print(apply(hex, 2, call)); })

  print(h2o.ls())
  print(hex)

  Log.info("Now apply but reverse order of kalls")
  lapply(rev(kalls), function(call) { print(apply(hex, 2, call)); })

  print(h2o.ls())
  print(hex)

  # Apply on a complex anonymous function
  Log.info("Now try some misc. apply calls")
  print(apply(hex, 2, function(x) { abs( x*x - x*5*x ) - 55/x; abs(x*x*x - 999/mean(x[1:20,])*x ) }))

  print(h2o.ls())

  # Same thing, but from an R variable
  f <- function(x) { abs( x*x - x*5*x ) - 55/x; abs(x*x*x - 999/mean(x[1:20,])*x ) }
  print(apply(hex, 2, f))
  gc()
  print(h2o.ls())

  # Apply, with some R-defined user-defined functions
  x <- function(x) { stop("shadowed function x, so never call me") }
  sqr  <- function(x) { x*x }
  quad <- function(x) { sqr(x)*sqr(x) }
  print(apply( hex, 2, function(x) { quad(x) }))
  gc()
  print(h2o.ls())

  zzz <- 2.5
  print(apply( hex, 2, function(x) { zzz }))


  # PUBDEV-1749
  fun1 <- function(x) { x + x }
  fun <-  function(x) { x * fun1(x) }
  print(apply(hex, 2, fun))


  lookup <- as.h2o(data.frame(matrix(rnorm(100), ncol=50, nrow=50)))
  i <- 4 
  print(apply(as.h2o(1:50), 1, function(x) { lookup[i,] - lookup[10,] }))

  
}

# doesn't include issues with NAs! 
doTest("Check several cases of `apply` call ", test.apply)
