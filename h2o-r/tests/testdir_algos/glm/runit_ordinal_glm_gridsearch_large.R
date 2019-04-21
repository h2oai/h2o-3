setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)

# This test makes sure gridsearch work with ordinal regression.  It is okay here not to have expect_true
# tests here at the end of the test
glmOrdinalGrid <- function() {
  Dtrain <- h2o.uploadFile(locate("bigdata/laptop/glm_ordinal_logit/ordinal_multinomial_training_set.csv"))  
  Dtrain$C11 <- h2o.asfactor(Dtrain$C11)
  X   <- c(1:10)  
  Y<-"C11"
  Log.info("Build the gridsearch model")
  alphas <- c(0.01, 0.3, 0.5)
  lambdas <- c(1e-5, 1e-6, 1e-7, 1e-8)
  hyper_params = list(alpha=alphas, lambda=lambdas)
  model.grid <- h2o.grid("glm", y=Y, x=X, family='ordinal', training_frame=Dtrain, hyper_params=hyper_params)
  print(model.grid)
}

doTest("GLM: Ordinal Gridsearch with multinomial dataset ", glmOrdinalGrid)
