setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glrm.benign <- function() {
  Log.info("Importing benign.csv data...\n")
  benign.hex <- h2o.uploadFile(locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)

  rank <- seq(from = 8, to = 14, by = 2)
  for( i in rank ) {
    Log.info(paste("H2O GLRM with rank", i, "decomposition:\n"))
    benign.glrm <- h2o.glrm(training_frame = benign.hex, k = i, init = "PlusPlus", recover_svd = TRUE)
    print(benign.glrm)
    h2o.rm(benign.glrm@model$loading_key$name)   # Remove loading matrix to free memory
  }
  
  testEnd()
}

doTest("GLRM Test: Benign Data with Missing Entries", test.glrm.benign)
