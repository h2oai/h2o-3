setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GLM.covtype <- function() {
  h2oTest.logInfo("Importing covtype.20k.data...\n")
  covtype.hex = h2o.uploadFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"))
  
  myY = 55
  myX = setdiff(1:54, c(21,29))   # Cols 21 and 29 are constant, so must be explicitly ignored
  myX = myX[which(myX != myY)];
  
  # Set response to be indicator of a particular class
  res_class = sample(c(1,4), size = 1)
  h2oTest.logInfo(paste("Setting response column", myY, "to be indicator of class", res_class, "\n"))
  covtype.hex[,myY] <- covtype.hex[,myY] == res_class
  
  covtype.sum = summary(covtype.hex)
  print(covtype.sum)
  
  # L2: alpha = 0, lambda = 0
  start = Sys.time()
  covtype.h2o1 = h2o.glm(y = myY, x = myX, training_frame = covtype.hex, family = "binomial", nfolds = 2, alpha = 0, lambda = 0)
  end = Sys.time()
  h2oTest.logInfo(cat("GLM (L2) took", as.numeric(end-start), "seconds\n"))
  print(covtype.h2o1)

  covtype.h2o1 <- h2o.getModel(covtype.h2o1@model_id)
  
  # Elastic: alpha = 0.5, lambda = 1e-4
  start = Sys.time()
  covtype.h2o2 = h2o.glm(y = myY, x = myX, training_frame = covtype.hex, family = "binomial", nfolds = 2, alpha = 0.5, lambda = 1e-4)
  end = Sys.time()
  h2oTest.logInfo(cat("GLM (Elastic) took", as.numeric(end-start), "seconds\n"))
  print(covtype.h2o2)

  covtype.h2o2 <- h2o.getModel(covtype.h2o2@model_id)
  
  # L1: alpha = 1, lambda = 1e-4
  start = Sys.time()
  covtype.h2o3 = h2o.glm(y = myY, x = myX, training_frame = covtype.hex, family = "binomial", nfolds = 2, alpha = 1, lambda = 1e-4)
  end = Sys.time()
  h2oTest.logInfo(cat("GLM (L1) took", as.numeric(end-start), "seconds\n"))
  print(covtype.h2o3)

  covtype.h2o3 <- h2o.getModel(covtype.h2o3@model_id)


  print(covtype.h2o3)
  print(covtype.h2o2)
  print(covtype.h2o1)

  
  
}

h2oTest.doTest("Test GLM on covtype(20k) dataset", test.GLM.covtype)

