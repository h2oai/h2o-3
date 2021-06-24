setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)

glmOrdinal <- function() {
  D <- h2o.uploadFile(locate("smalldata/glm_ordinal_logit/ordinal_nidhi_small.csv"))  
  D$apply <- h2o.ifelse(D$apply == "unlikely", 0, h2o.ifelse(D$apply == "somewhat likely", 1, 2)) # reset levels from Megan Kurka
  D$apply <- h2o.asfactor(D$apply)
  D$pared <- as.factor(D$pared)
  D$public <- as.factor(D$public)
  X   <- c("pared", "public", "gpa")  
  Y<-"apply"
  Log.info("Build the model")
  obj_regL <- c(1/h2o.nrow(D), 1/(10*h2o.nrow(D)), 1/(100*h2o.nrow(D)))
  lambdaL <- c(1/h2o.nrow(D), 1/(10*h2o.nrow(D)), 1/(100*h2o.nrow(D)))
  alphaL <- c(0,0.5,1)
  bestAccLH <- 0
  bestAccSQERR <- 0
  seeds<-12345
  
  for (reg in obj_regL) {
    for (lambda in lambdaL) {
      for (alpha in alphaL) {
        print(c(reg, lambda, alpha))
    m1 <- h2o.glm(y = Y, x = X, training_frame = D, lambda=c(reg/10), alpha=c(0.5), family = "ordinal", beta_epsilon=1e-8, 
                objective_epsilon=1e-6, obj_reg=reg,max_iterations=8000, seed=seeds)  
    predh2o = as.data.frame(h2o.predict(m1,D))
    Ddata <- as.data.frame(D)
    confusionH2O <- table(Ddata$apply, predh2o$predict)
    print("Performance with solver set to default of set to GRADIENT_DESCENT_LH")
    print(confusionH2O)
    accRH2O <- calAccuracy(as.numeric(Ddata$apply), as.numeric(predh2o$predict))
    print("accuracy")
    print(accRH2O)
    if (accRH2O > bestAccLH)
      bestAccLH <- accRH2O
    
    m1 <- h2o.glm(y = Y, x = X, training_frame = D, lambda=c(reg/100), alpha=c(0.5), family = "ordinal", beta_epsilon=1e-8, 
                  objective_epsilon=1e-6, obj_reg=reg,max_iterations=8000, solver='GRADIENT_DESCENT_SQERR', seed=seeds)  
    predh2o = as.data.frame(h2o.predict(m1,D))
    Ddata <- as.data.frame(D)
    confusionH2O <- table(Ddata$apply, predh2o$predict)
    print("Performance with solver GRADIENT_DESCENT_SQERR:")
    print(confusionH2O)
    accRH2O <- calAccuracy(as.numeric(Ddata$apply), as.numeric(predh2o$predict))
    if (accRH2O > bestAccSQERR) {
      bestAccSQERR <- accRH2O
    }
    print("accuracy")
     print(accRH2O)
      }
    }
  }
  print("Best accuracies from solver GRADIENT_DESCENT_SQERR and GRADIENT_DESCENT_LH")
  print(c(bestAccSQERR, bestAccLH))
  expect_true(bestAccSQERR >= bestAccLH)

  D2 <- h2o.uploadFile(locate("smalldata/glm_ordinal_logit/ordinal_nidhi_small.csv"), destination_frame="covtype.hex")  
  dat <- as.data.frame(D2)
  dat$apply <- factor(dat$apply, levels=c("unlikely", "somewhat likely", "very likely"), ordered=TRUE)
  m <- polr(apply ~ pared + public + gpa, data = dat, Hess=FALSE)
  predictedClassR <- predict(m, dat)
  rPred <- predict(m, dat, type="p")
  confusionR <- table(dat$apply, predictedClassR)
  accR <- (confusionR[1,1]+confusionR[2,2])/400
  print(confusionR)
  print(accR)
  expect_true((abs(bestAccSQERR-accR) < 0.01) || bestAccSQERR > accR) # compare performance level
}

doTest("GLM: Ordinal with data found by Nidhi at https://stats.idre.ucla.edu/stat/data/ologit.dta", glmOrdinal)
