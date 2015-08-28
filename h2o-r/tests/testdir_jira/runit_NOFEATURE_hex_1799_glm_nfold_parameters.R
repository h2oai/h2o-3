######################################################################################
# Test for HEX-1799
# h2o.glm with nfolds >= 2 should have model parameters that match the main glm model.
######################################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

heading("BEGIN TEST")


hex_1799_test <-
function(conn) {

  path <- locate("smalldata/logreg/prostate.csv")
  prostate.hex <- h2o.importFile(path, destination_frame="prostate.hex")
  
  main_model <- h2o.glm(x = 3:8, y = 2, training_frame = prostate.hex, nfolds = 2, standardize = FALSE, family = "binomial")


  print(main_model@key)
  
  print(conn)
  
  first_xval <- h2o.getModel(main_model@model_id)@xval[[1]]

  Log.info("Expect that the xval model has a family binomial, just like the main model...")
  expect_that(first_xval@model$params$family$family, equals("binomial"))
  expect_that(first_xval@model$params$family$family, equals(main_model@model$params$family$family))
  
  Log.info("Expect that the xval model has standardize set to FALSE as it is in the main model.")
  expect_that(first_xval@model$params$standardize, equals("FALSE"))
  expect_that(as.logical(first_xval@model$params$standardize), equals(main_model@model$params$standardize))
  testEnd()
}


doTest("Perform the test for hex 1799", hex_1799_test)
