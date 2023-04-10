setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.aic.likelihood <- function() {
  # checking for Gaussian
  h2o.data = h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")    
  R.data = as.data.frame(as.matrix(h2o.data))
  myY = "GLEASON"
  myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  R.formula = (R.data[,"GLEASON"]~.) 
  model.h2o.gaussian.identity <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="gaussian", link="identity",alpha=0.5, lambda=0, nfolds=0)
  model.R.gaussian.identity <- glm(formula=R.formula, data=R.data[,2:9], family=gaussian(link=identity), na.action=na.omit)
  perf <- h2o.performance(model.h2o.gaussian.identity, h2o.data)
  print("GLM Gaussian")
  print("H2O AIC")
  print(h2o.aic(perf))
  print("H2O log likelihood")
  # print(h2o.loglikelihood(perf)) # Yuliia: please implement this
  print("R AIC")
  print(AIC(model.R.gaussian.identity))
  print("R loglikelihood")
  print(logLik(model.R.gaussian.identity))
 # browser()
  
  # check for Gamma
  myY = "DPROS"
  myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","GLEASON")
  R.formula = (R.data[,"DPROS"]~.) 
  model.h2o.gamma.inverse <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="gamma", link="inverse",alpha=0.5, lambda=0, nfolds=0)
  model.R.gamma.inverse <- glm(formula=R.formula, data=R.data[,c(1:5,7:9)], family=Gamma(link=inverse), na.action=na.omit)
  perf <- h2o.performance(model.h2o.gamma.inverse, h2o.data)
  print("GLM Gamma")
  print("H2O AIC")
  print(h2o.aic(perf))
  print("H2O log likelihood")
  # print(h2o.loglikelihood(perf)) # Yuliia: please implement this
  print("R AIC")
  print(AIC(model.R.gamma.inverse))
  
  # checking for binomial
  myY <- "CAPSULE"
  myX <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  R.formula <- (R.data[,"CAPSULE"]~.)
  model.h2o.binomial.logit <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="binomial", link="logit",alpha=0.5, lambda=0, nfolds=0)
  model.R.binomial.logit <- glm(formula=R.formula, data=R.data[,4:10], family=binomial(link=logit), na.action=na.omit)
  perf <- h2o.performance(model.h2o.binomial.logit, h2o.data)
  print("GLM binomial")
  print("H2O AIC")
  print(h2o.aic(perf))
  print("H2O log likelihood")
  # print(h2o.loglikelihood(perf)) # Yuliia: please implement this
  print("R AIC")
  print(AIC(model.R.binomial.logit))
  print("R loglikelihood")
  print(logLik(model.R.binomial.logit))
  
  # checking for Poisson
  myY = "GLEASON"
  myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  R.formula = (R.data[,"GLEASON"]~.) 
  model.h2o.poisson.log <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="poisson", link="log",alpha=0.5, lambda=0, nfolds=0)
  model.R.poisson.log <- glm(formula=R.formula, data=R.data[,2:9], family=poisson(link=log), na.action=na.omit)
  perf <- h2o.performance(model.h2o.poisson.log, h2o.data)
  print("GLM Poisson")
  print("H2O AIC")
  print(h2o.aic(perf))
  print("H2O log likelihood")
  # print(h2o.loglikelihood(perf)) # Yuliia: please implement this
  print("R AIC")
  print(AIC(model.R.poisson.log))
  print("R loglikelihood")
  print(logLik(model.R.poisson.log))
}

doTest("Testing AIC/likelihood for GLM", test.glm.aic.likelihood)
