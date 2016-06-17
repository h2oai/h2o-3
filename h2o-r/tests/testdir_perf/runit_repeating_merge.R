setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

# PUBDEV-2964: h2o.merge(,method="radix") failing 15/40 runs
# TODO: randomize data overlap and distribution much more

test.merge = function(Ns=c(1e6,5e6), numRepeats=40) {
  started.at = proc.time()
  
  for (N in Ns) {
    cat("N=",N,"... repeat ")
    for (i in 1:numRepeats) {
      cat(i)
      capture.output(X <- h2o.createFrame(rows=N, cols=2, integer_range=N, integer_fraction=1,
        categorical_fraction=0,binary_fraction=0,missing_fraction=0))
      X$C1 = abs(X$C1)
      colnames(X) = c("KEY","X1")

      capture.output(Y <- h2o.createFrame(rows=N,cols=2, integer_range=N, integer_fraction=1,
        categorical_fraction=0,binary_fraction=0,missing_fraction=0))
      Y$C1 = abs(Y$C1)
      colnames(Y) = c("KEY","Y1")
      
      ans = h2o.merge(X, Y, method="radix")
      expect_true(abs(nrow(ans)/N - 1) < 0.05)
      rm(ans)
      rm(X)
      rm(Y)
      gc()
    }
    cat("\n")
  }
  cat("\nTotal time taken :", round((proc.time() - started.at)[["elapsed"]]/60, 1), "mins \n")  
}

doTest("Test repeating merge", test.merge)


