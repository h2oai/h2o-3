setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GLM.prostate <- function(conn) {
  Log.info("Importing prostate.csv data...\n")
  # prostate.hex = h2o.importURL(conn, "https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv", "prostate.hex")
  # prostate.hex = h2o.importFile(conn, normalizePath("../../../smalldata/logreg/prostate.csv"), "prostate.hex")
  prostate.hex = h2o.uploadFile(conn, locate("smalldata/logreg/prostate.csv"), "prostate.hex")
  prostate.sum = summary(prostate.hex)
  print(prostate.sum)
  
  # prostate.data = read.csv(text = getURL("https..//raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data = read.csv(locate("smalldata/logreg/prostate.csv"), header = TRUE)
  prostate.data = na.omit(prostate.data)
  
  myY = 2 
  for(maxx in 4:9) {
    myX = 3:maxx
    myX = myX[which(myX != myY)]
    # myX = paste(myX, collapse=",")
    
    Log.info(cat("B)H2O GLM (binomial) with parameters:\nX:", myX, "\nY:", myY, "\n"))
    prostate.glm.h2o = h2o.glm(y = myY, x = myX, training_data = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
    print(prostate.glm.h2o)
    
    # prostate.glm = glm.fit(y = prostate.data[,myY], x = prostate.data[,myX], family = binomial)
    prostate.glm = glmnet(y = prostate.data[,myY], x = data.matrix(prostate.data[,myX]), family = "binomial", alpha = 0.5)
    checkGLMModel(prostate.glm.h2o, prostate.glm)
  }
  
  testEnd()
}

doTest("GLM Test: Prostate", test.GLM.prostate)

