setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_save_model.golden <- function() {

  prostate.hex <- h2o.uploadFile(path = h2oTest.locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", alpha = 0.5)
  h2o.saveModel(object = prostate.glm, path = h2oTest.sandbox(), force = TRUE)
  
}

h2oTest.doTest("R Doc Save Model", test.rdoc_save_model.golden)
