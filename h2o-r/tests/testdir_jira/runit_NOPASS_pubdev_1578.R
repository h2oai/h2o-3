setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1578 <- function() {
  Log.info("Importing prostate data...")
  prostate.train <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
  glm.model.A <- h2o.glm(x = 3:9, y = 2, training_frame = prostate.train, family = "binomial", model_id = 'prostate.glm.model')
  glm.model.B <- h2o.getModel('prostate.glm.model')

  expect_equal(glm.model.A@model, glm.model.B@model)

  testEnd()
}

doTest("PUBDEV-1578: GLM models are different", test.pubdev.1578)
