setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
glmMultinomial <- function() {
  D <- h2o.uploadFile(h2oTest.locate("smalldata/covtype/covtype.20k.data"), destination_frame="covtype.hex")  
  D[,55] <- as.factor(D[,55])
  Y <- 55
  X   <- 1:54  
  
  h2oTest.logInfo("Build the model")
  m1 <- h2o.glm(y = Y, x = X, training_frame = D, family = "multinomial", alpha = 0.99, solver='IRLSM')  
  m2 <- h2o.glm(y = Y, x = X, training_frame = D, family = "multinomial", alpha = 0.99, solver = 'L_BFGS',max_iter=500)  
  print(m1)
  print(m2)  
  checkTrue(m1@model$training_metrics@metrics$residual_deviance <= 26309.91 + 1, "residual deviance too high")
  checkTrue(m2@model$training_metrics@metrics$residual_deviance <= 26000.00 + 0, "residual deviance too high")
}
h2oTest.doTest("GLM: Multinomial", glmMultinomial)
