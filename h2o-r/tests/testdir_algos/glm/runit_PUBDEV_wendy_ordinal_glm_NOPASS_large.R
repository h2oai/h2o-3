setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)
options(warn=-1)


glmOrdinal <- function() {
  Dtrain <- h2o.uploadFile(locate("bigdata/laptop/glm_ordinal_logit/ordinal_ordinal_5_training_set.csv"))  
  Dtest <- h2o.uploadFile(locate("bigdata/laptop/glm_ordinal_logit/ordinal_ordinal_5_test_set.csv")) 
  
  D2 <- as.data.frame(Dtrain)
  D2$C6 <- factor(D2$C6)
  D2T <- as.data.frame(Dtest)
  D2T$C6 <- factor(D2T$C6)
  
  m <- polr(C6 ~ ., data = D2, Hess=TRUE)
  predictedClassR <- predict(m, D2T)
  rPred <- predict(m, D2T, type="p")
  confusionR <- table(D2T$C6, predictedClassR)
  print(confusionR)
  accR = calAccuracy(as.numeric(D2T$C6), as.numeric(predictedClassR))
  print(accR)
  if (length(levels(predh2o$predict)) < length(h2o.levels(Dtest$C6))) {
    print("Ran with bad seed.")
  } 
  
  Dtrain$C6 <- h2o.asfactor(Dtrain$C6)
  Dtest$C6 <- h2o.asfactor(Dtest$C6)
  h2o.describe(Dtrain)
  h2o.describe(Dtest)

  X   <- c(1:5)  
  Y<-"C6"
  Log.info("Build the model")
  reg <- 1/h2o.nrow(Dtrain)
 objR <- c(100*reg, 10*reg, reg, reg/10, reg/100)
 lambdaL <- c(0,100*reg, 10*reg, reg, reg/10, reg/100)
 alphaR <- c(0,0.2, 0.5,0.8,1)
 bestAcc <- 0
 bestParms <- c(0,0,0)
 bestConf <- c()
 for (regR in objR) {
   for (lambdaR in lambdaL) {
     for (alpha in alphaR) {
       params <- c(regR, lambdaR, alpha)
       print(params)
       m1 <- h2o.glm(y = Y, x = X, training_frame = Dtrain, lambda=c(lambdaR), alpha=c(alpha), family = "ordinal", 
                     beta_epsilon=1e-8, objective_epsilon=1e-8, obj_reg=regR,max_iterations=1000 )  
       temp <- h2o.predict(m1,Dtest)
       predh2o = as.data.frame(temp)
       Ddata <- as.data.frame(Dtest)
       confusionH2O <- table(Ddata$C6, predh2o$predict)
       print(confusionH2O) 
       acc = calAccuracy(as.numeric(Ddata$C6), as.numeric(predh2o$predict))
       if (bestAcc < acc) {
         bestAcc <- acc
         bestParms <- params
         bestConf <- confusionH2O
     }
       print(acc)
     }
   }
 }
 print("best acc is ")
 print(bestAcc)
 print(bestParms)
 print(bestConf)
}

doTest("GLM: Ordinal with random data", glmOrdinal)
