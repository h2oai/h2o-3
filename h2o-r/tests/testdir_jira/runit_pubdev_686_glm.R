setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Test for JIRA PUBDEV-686
##




test <- function() {
  print("Read allyears2k_headers.zip into R.")
  data.hex <-  h2o.importFile(h2oTest.locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="airlines.data")

  s = h2o.runif(data.hex)
  train = data.hex[s <= 0.8,]
  valid = data.hex[s > 0.8,]

  myY = "IsDepDelayed"
  myX = setdiff(names(data.hex), myY)

  # GLM - All columns are being filtered out due to NA content
  expect_error(h2o.glm(x = myX, y = myY, training_frame = data.hex, validation_frame = valid,
    family = "gaussian",missing_values_handling="Skip"))
}

h2oTest.doTest("GLM PUBDEV-686", test)
