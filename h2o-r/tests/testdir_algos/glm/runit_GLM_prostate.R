setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GLM.prostate <- function() {
  h2oTest.logInfo("Importing prostate.csv data...\n")
  prostate.hex = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"), "prostate.hex")
  prostate.sum = summary(prostate.hex)
  print(prostate.sum)
  
  prostate.data = read.csv(h2oTest.locate("smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data = na.omit(prostate.data)
  
  myY = 2 
  for(maxx in 4:9) {
    myX = 3:maxx
    myX = myX[which(myX != myY)]
    
    h2oTest.logInfo(cat("B)H2O GLM (binomial) with parameters:\nX:", myX, "\nY:", myY, "\n"))
    prostate.glm.h2o = h2o.glm(y = myY, x = myX, training_frame = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
    print(prostate.glm.h2o)

    prostate.glm = glmnet(y = prostate.data[,myY], x = data.matrix(prostate.data[,myX]), family = "binomial", alpha = 0.5)
    h2oTest.checkGLMModel(prostate.glm.h2o, prostate.glm)
  }
  
  
}

h2oTest.doTest("GLM Test: Prostate", test.GLM.prostate)

