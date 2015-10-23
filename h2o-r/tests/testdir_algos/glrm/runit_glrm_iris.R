


test.glrm.iris <- function() {
  Log.info("Importing iris_wheader.csv data...") 
  irisH2O <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"), destination_frame = "irisH2O")
  print(summary(irisH2O))
  
  for(t in c("NONE", "DEMEAN", "DESCALE", "STANDARDIZE")) {
    rank <- sample(1:7, 1)
    gx <- abs(runif(1)); gy <- abs(runif(1))
    Log.info(paste("H2O GLRM with rank k = ", rank, ", gamma_x = ", gx, ", gamma_y = ", gy, ", transform = '", t, "'", sep = ""))
    fitH2O <- h2o.glrm(irisH2O, k = rank, loss = "Quadratic", gamma_x = gx, gamma_y = gy, transform = t)
    Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))    
    checkGLRMPredErr(fitH2O, irisH2O, tolerance = 1e-5)
    h2o.rm(fitH2O@model$representation_name)   # Remove X matrix to free memory
  }
  
}

doTest("GLRM Test: Iris with Various Transformations", test.glrm.iris)
