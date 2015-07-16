setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GLM.covtype <- function(conn) {
  Log.info("Importing covtype.20k.data...\n")
  covtype.hex=h2o.uploadFile(conn, locate("smalldata/covtype/covtype.altered.gz"))
  
  myY=55
  myX=setdiff(1:54, c(21,29))   # Cols 21 and 29 are constant, so must be explicitly ignored
  myX=myX[which(myX != myY)];
  
  # Set response to be indicator of a particular class
  res_class=sample(c(1,4), size=1)
  Log.info(paste("Setting response column", myY, "to be indicator of class", res_class, "\n"))
  covtype.hex[,myY] <- covtype.hex[,myY] == res_class
  
  covtype.sum=summary(covtype.hex)
  print(covtype.sum)
  
  # L2: alpha=0, lambda=0
  start=Sys.time()
  covtype.h2o1=h2o.startGLMJob(y=myY, x=myX, training_frame=covtype.hex, family="binomial", alpha=0, lambda=0)
  end=Sys.time()
  Log.info(cat("GLM (L2) on", covtype.hex@frame_id, "took", as.numeric(end-start), "seconds\n"))
  print(covtype.h2o1)
  
  # Elastic: alpha=0.5, lambda=1e-4
  start=Sys.time()
  covtype.h2o2=h2o.startGLMJob(y=myY, x=myX, training_frame=covtype.hex, family="binomial", alpha=0.5, lambda=1e-4)
  end=Sys.time()
  Log.info(cat("GLM (Elastic) on", covtype.hex@frame_id, "took", as.numeric(end-start), "seconds\n"))
  print(covtype.h2o2)

  # L1: alpha=1, lambda=1e-4
  start=Sys.time()
  covtype.h2o3=h2o.startGLMJob(y=myY, x=myX, training_frame=covtype.hex, family="binomial", alpha=1, lambda=1e-4)
  end=Sys.time()
  Log.info(cat("GLM (L1) on", covtype.hex@frame_id, "took", as.numeric(end-start), "seconds\n"))
  print(covtype.h2o3)

  covtype.h2o1 <- h2o.getFutureModel(covtype.h2o1)
  print(covtype.h2o1)
  covtype.h2o2 <- h2o.getFutureModel(covtype.h2o2)
  print(covtype.h2o2)
  covtype.h2o3 <- h2o.getFutureModel(covtype.h2o3)
  print(covtype.h2o3)
  
  testEnd()
}

doTest("Test GLM on covtype(20k) dataset", test.GLM.covtype)

