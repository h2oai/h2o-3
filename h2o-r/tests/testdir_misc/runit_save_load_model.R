setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test: Saving and Loading H2O Models
# Description: Build an H2O model, save it, then load it again in H2O and verify no information was lost
##




test.save_load_dlmodel <- function() {
  temp_dir = h2oTest.sandbox()

  # Test saving and loading of Deep Learning model with validation dataset
  h2oTest.logInfo("Importing prostate_train.csv and prostate_test.csv...")
  prostate.train = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate_train.csv"), "prostate.train")
  prostate.test = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate_test.csv"), "prostate.test")

  h2oTest.logInfo("Build Deep Learning model and save to disk")
  prostate.dl = h2o.deeplearning(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"), training_frame = prostate.train, validation_frame = prostate.test)
  print(prostate.dl)
  prostate.dl.path = h2o.saveModel(object = prostate.dl, path=temp_dir, force = TRUE)

  h2oTest.logInfo(paste("Load Deep Learning model saved at", prostate.dl.path))
  prostate.dl2 = h2o.loadModel(prostate.dl.path)

  expect_equal(class(prostate.dl), class(prostate.dl2))
  expect_equal(prostate.dl@allparameters, prostate.dl2@allparameters)
  # FIXME there is no field data in the model
  ## expect_equal(prostate.dl@data, prostate.dl2@data)
  # FIXME models cannot be compared
  expect_equal(prostate.dl@model, prostate.dl2@model)
  # FIXME there is no field valid in the model
  ## expect_equal(prostate.dl@valid, prostate.dl2@valid)

}

h2oTest.doTest("R Save and Load Deep Learning Model", test.save_load_dlmodel)

