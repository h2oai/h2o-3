setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_save_model.golden <- function(H2Oserver) {
  prostate.hex <- h2o.importFile(H2Oserver, path = "smalldata/logreg/prostate.csv", key = "prostate.hex")
  prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.hex, family = "binomial", nfolds = 10, alpha = 0.5)
  h2o.saveModel(object = prostate.glm, dir = tempdir(), save_cv = TRUE, force = TRUE)
  testEnd()
}

doTest("R Doc Save Model", test.rdoc_save_model.golden)
