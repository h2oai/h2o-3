setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.glrm.benign <- function() {
  h2oTest.logInfo("Importing benign.csv data...\n")
  benign.hex <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)

  rank <- seq(from = 8, to = 14, by = 2)
  for( i in rank ) {
    h2oTest.logInfo(paste("H2O GLRM with rank", i, "decomposition:\n"))
    benign.glrm <- h2o.glrm(training_frame = benign.hex, k = i, init = "SVD", recover_svd = TRUE)
    h2oTest.logInfo(paste("Iterations:", benign.glrm@model$iterations, "\tFinal Objective:", benign.glrm@model$objective))    
    h2oTest.logInfo(paste("Singular values:", paste(benign.glrm@model$singular_vals, collapse = " ")))
    h2oTest.logInfo("Eigenvectors:"); print(benign.glrm@model$eigenvectors)
    h2oTest.checkGLRMPredErr(benign.glrm, benign.hex, tolerance = 1e-6)
    h2o.rm(benign.glrm@model$representation_name)   # Remove X matrix to free memory
  }
  
}

h2oTest.doTest("GLRM Test: Benign Data with Missing Entries", test.glrm.benign)
