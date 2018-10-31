setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
library(MASS)

glmMultinomial <- function() {
  D <- h2o.importFile(locate("bigdata/laptop/glm/multinomial20Class_10KRows.csv")) 
  X <- c(1:h2o.ncol(D)-1)
  Y <- h2o.ncol(D)
  D["C1"] <- h2o.asfactor(D["C1"])
  D["C2"] <- h2o.asfactor(D["C2"])
  D["C3"] <- h2o.asfactor(D["C3"])
  D["C4"] <- h2o.asfactor(D["C4"])
  D["C5"] <- h2o.asfactor(D["C5"])
  D["C6"] <- h2o.asfactor(D["C6"])
  D["C7"] <- h2o.asfactor(D["C7"])
  D["C8"] <- h2o.asfactor(D["C8"])
  D["C9"] <- h2o.asfactor(D["C9"])
  D["C10"] <- h2o.asfactor(D["C10"])
  D["C11"] <- h2o.asfactor(D["C11"])
  D["C12"] <- h2o.asfactor(D["C12"])
  D["C13"] <- h2o.asfactor(D["C13"])
  D["C79"] <- h2o.asfactor(D["C79"])
  
  seeds<-12345
  original_model <- h2o.loadModel(locate("bigdata/laptop/glm/GLM_model_python_1542751701212_3"))
  multinomialModel <- h2o.glm(y=Y, x=X, training_frame=D, family='multinomial', seed = seeds, solver="COORDINATE_DESCENT",max_iterations=5)

  coeffNew = multinomialModel@model$coefficients
  coeffOld = original_model@model$coefficients
  # compare two coefficients
  expect_true(sum(abs(coeffNew-coeffOld) < 1e-10)==length(coeffOld), "Coefficients from two models are different.")
  print("Test completed successfully.")
  }

doTest("GLM: checking multinomial coefficients before and after PUBDEV-5876.", glmMultinomial)
