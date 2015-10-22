


test.glrm.benign <- function() {
  Log.info("Importing benign.csv data...\n")
  benign.hex <- h2o.uploadFile(locate("smalldata/logreg/benign.csv"))
  benign.sum <- summary(benign.hex)
  print(benign.sum)

  rank <- seq(from = 8, to = 14, by = 2)
  for( i in rank ) {
    Log.info(paste("H2O GLRM with rank", i, "decomposition:\n"))
    benign.glrm <- h2o.glrm(training_frame = benign.hex, k = i, init = "SVD", recover_svd = TRUE)
    Log.info(paste("Iterations:", benign.glrm@model$iterations, "\tFinal Objective:", benign.glrm@model$objective))    
    Log.info(paste("Singular values:", paste(benign.glrm@model$singular_vals, collapse = " ")))
    Log.info("Eigenvectors:"); print(benign.glrm@model$eigenvectors)
    checkGLRMPredErr(benign.glrm, benign.hex, tolerance = 1e-6)
    h2o.rm(benign.glrm@model$representation_name)   # Remove X matrix to free memory
  }
  
}

doTest("GLRM Test: Benign Data with Missing Entries", test.glrm.benign)
