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
    m1 <- h2o.glm(y = Y, x = X, training_frame = D, lambda=c(0.00250), alpha=c(0.5), family = "ordinal", beta_epsilon=1e-8, 
                objective_epsilon=1e-10, obj_reg=0.00025,max_iterations=1000 )  
    predh2o = as.data.frame(h2o.predict(m1,D))
    Ddata <- as.data.frame(D)
    confusionH2O <- table(Ddata$apply, predh2o$predict)
    print(confusionH2O)
    accH2O <- (confusionH2O[1,1]+confusionH2O[2,2])/338
    print(accH2O)

  D2 <- h2o.uploadFile(locate("smalldata/glm_ordinal_logit/ordinal_nidhi_small.csv"), destination_frame="covtype.hex")  
  dat <- as.data.frame(D2)
  dat$apply <- factor(dat$apply, levels=c("unlikely", "somewhat likely", "very likely"), ordered=TRUE)
  m <- polr(apply ~ pared + public + gpa, data = dat, Hess=FALSE)
  predictedClassR <- predict(m, dat)
  rPred <- predict(m, dat, type="p")
  confusionR <- table(dat$apply, predictedClassR)
  accR <- (confusionR[1,1]+confusionR[2,2])/338
  print(confusionR)
  print(accR)
  expect_true((abs(accH2O-accR) < 0.01) || (accH2O>accR)) # compare performance level
}

doTest("GLM: Ordinal with data found by Nidhi at https://stats.idre.ucla.edu/stat/data/ologit.dta", glmOrdinal)
