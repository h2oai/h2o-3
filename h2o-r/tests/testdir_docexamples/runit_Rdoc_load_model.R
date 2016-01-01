setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_load_model.golden <- function() {

  prostate.hex <- h2o.uploadFile(path = h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", alpha = 0.5)
  glmmodel.path <- h2o.saveModel(object = prostate.glm, path = h2oTest.sandbox())
  glmmodel.load <- h2o.loadModel(glmmodel.path)
  
}

h2oTest.doTest("R Doc Load Model", test.rdoc_load_model.golden)
