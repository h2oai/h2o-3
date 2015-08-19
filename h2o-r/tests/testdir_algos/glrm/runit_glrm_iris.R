setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glrm.iris <- function() {
  Log.info("Importing iris_wheader.csv data...") 
  irisR <- read.csv(locate("smalldata/iris/iris_wheader.csv"), header = TRUE)
  irisH2O <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"), destination_frame = "airH2O")
  print(summary(irisH2O))
  
  for(t in c("NONE", "DEMEAN", "DESCALE", "STANDARDIZE")) {
    rank <- sample(1:7, 1)
    gx <- abs(runif(1)); gy <- abs(runif(1))
    Log.info(paste("H2O GLRM with rank k = ", rank, ", gamma_x = ", gx, ", gamma_y = ", gy, ", transform = '", t, "'", sep = ""))
    fitH2O <- h2o.glrm(irisH2O, k = rank, loss = "L2", gamma_x = gx, gamma_y = gy, transform = t)
    Log.info(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))    
    h2o.rm(fitH2O@model$loading_key$name)   # Remove loading matrix to free memory
  }
  testEnd()
}

doTest("GLRM Test: Iris with Various Transformations", test.glrm.iris)
