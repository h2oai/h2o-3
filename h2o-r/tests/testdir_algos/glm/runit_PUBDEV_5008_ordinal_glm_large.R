setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)

glmOrdinal <- function() {
  Dtrain <- h2o.uploadFile(locate("bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv"))  
  Dtest <- h2o.uploadFile(locate("bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_test_set.csv")) 
  Dtrain$C11 <- h2o.asfactor(Dtrain$C11)
  Dtest$C11 <- h2o.asfactor(Dtest$C11)
  h2o.describe(Dtrain)
  h2o.describe(Dtest)

  X   <- c(1:10)  
  Y<-"C11"
  Log.info("Build the model")

  m1 <- h2o.glm(y = Y, x = X, training_frame = Dtrain, lambda=c(0.000000001), alpha=c(0.7), family = "ordinal", 
                beta_epsilon=1e-8, objective_epsilon=1e-10, obj_reg=0.00001,max_iterations=1000 )  
  temp <- h2o.predict(m1,Dtest)
  predh2o = as.data.frame(temp)
  Ddata <- as.data.frame(Dtest)
  confusionH2O <- table(Ddata$C11, predh2o$predict)
  print(confusionH2O)
  acc = calAccuracy(as.numeric(Ddata$C11), as.numeric(predh2o$predict))
  print(acc)

  D2 <- as.data.frame(Dtrain)
  D2$C11 <- factor(D2$C11)
  D2T <- as.data.frame(Dtest)
  D2T$C11 <- factor(D2T$C11)

  m <- polr(C11 ~ ., data = D2, Hess=TRUE)
  predictedClassR <- predict(m, D2T)
  rPred <- predict(m, D2T, type="p")
  confusionR <- table(D2T$C11, predictedClassR)
  print(confusionR)
  accR = calAccuracy(as.numeric(D2T$C11), as.numeric(predictedClassR))
  print(accR)
  if (length(levels(predh2o$predict)) < length(h2o.levels(Dtest$C11))) {
    print("Ran with bad seed.")
  } else {
  expect_true(abs(acc-accR) < 0.1) # compare performance level
  expect_true(length(levels(predh2o$predict))==length(h2o.levels(Dtest$C11)))
}
}

doTest("GLM: Ordinal with random data", glmOrdinal)
