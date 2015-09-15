setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_load_model.golden <- function() {
  prostate.hex <- h2o.uploadFile(path = locate("smalldata/logreg/prostate.csv"), destination_frame = "prostate.hex")
  # prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
  prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", alpha = 0.5)
  glmmodel.path <- h2o.saveModel(object = prostate.glm, path = tempdir())
  glmmodel.load <- h2o.loadModel(glmmodel.path)
  testEnd()
}

doTest("R Doc Load Model", test.rdoc_load_model.golden)
