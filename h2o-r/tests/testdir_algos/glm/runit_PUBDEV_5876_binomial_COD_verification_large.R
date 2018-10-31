setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)

glmBinomial <- function() {
  D <- h2o.importFile(locate("bigdata/laptop/glm/binomial_binomial_training_set_enum_trueOneHot.csv.zip")) 
  X <- c(1:h2o.ncol(D)-1)
  Y <- h2o.ncol(D)

  D["C1"] <- h2o.asfactor(D["C1"])
  D["C2"] <- h2o.asfactor(D["C2"])
  D["C3"] <- h2o.asfactor(D["C3"])
  D["C4"] <- h2o.asfactor(D["C4"])
  D["C5"] <- h2o.asfactor(D["C5"])
  D["C6"] <- h2o.asfactor(D["C6"])
  D["C7"] <- h2o.asfactor(D["C7"])
  D["C79"] <- h2o.asfactor(D["C79"])
  
  seeds<-12345
  original_model <- h2o.loadModel(locate("bigdata/laptop/glm/GLM_model_python_1542751701212_1"))
  binomialModel <- h2o.glm(y=Y, x=X, training_frame=D, family='binomial', seed = seeds, solver="COORDINATE_DESCENT")

  coeffNew = binomialModel@model$coefficients
  coeffOld = original_model@model$coefficients
  # compare two coefficients
  expect_true(sum(abs(coeffNew-coeffOld) < 1e-10)==length(coeffOld), "Coefficients from two models are different.")
  print("Test completed successfully.")
  }

doTest("GLM: checking binomial coefficients before and after PUBDEV-5876.", glmBinomial)
