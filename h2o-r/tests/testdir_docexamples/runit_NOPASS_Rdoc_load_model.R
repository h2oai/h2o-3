setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_load_model.golden <- function(H2Oserver) {
  prostate.hex = h2o.importFile(H2Oserver, path = "https://raw.github.com/0xdata/h2o/master/smalldata/logreg/prostate.csv", key = "prostate.hex")
  prostate.glm = h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
  glmmodel.path = h2o.saveModel(object = prostate.glm, dir = tempdir())
  glmmodel.load = h2o.loadModel(H2Oserver, glmmodel.path)
  testEnd()
}

doTest("R Doc Load Model", test.rdoc_load_model.golden)

