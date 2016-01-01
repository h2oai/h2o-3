setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.glrm.iris <- function() {
  h2oTest.logInfo("Importing iris_wheader.csv data...") 
  irisH2O <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"), destination_frame = "irisH2O")
  print(summary(irisH2O))
  
  num_cols <- colnames(irisH2O)[1:4]   # sepal_len, sepal_wid, petal_len, and petal_wid
  for(t in c("NONE", "DEMEAN", "DESCALE", "STANDARDIZE")) {
    rank <- sample(1:7, 1)
    gx <- abs(runif(1)); gy <- abs(runif(1))
    h2oTest.logInfo(paste("H2O GLRM with rank k = ", rank, ", gamma_x = ", gx, ", gamma_y = ", gy, ", transform = '", t, "'", sep = ""))
    fitH2O <- h2o.glrm(irisH2O, k = rank, loss = "Quadratic", gamma_x = gx, gamma_y = gy, transform = t, impute_original = FALSE)
    h2oTest.logInfo(paste("Iterations:", fitH2O@model$iterations, "\tFinal Objective:", fitH2O@model$objective))    
    h2oTest.checkGLRMPredErr(fitH2O, irisH2O, tolerance = 1e-5)
    
    h2oTest.logInfo("Projected archetypes should equal original on numeric features")
    arch_orig <- as.data.frame(fitH2O@model$archetypes)
    arch_proj <- as.data.frame(h2o.proj_archetypes(fitH2O, irisH2O, reverse_transform = FALSE))
    expect_equivalent(arch_proj[,num_cols], arch_orig[,num_cols])
    h2o.rm(fitH2O@model$representation_name)   # Remove X matrix to free memory
  }
}

h2oTest.doTest("GLRM Test: Iris with Various Transformations", test.glrm.iris)
