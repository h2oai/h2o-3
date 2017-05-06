setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test is written to add variance metrics for GLRM suggested by Erin in PUBDEV-3501.
test.glrm.pubdev_3501.variance.metrics <- function() {
#  ausPath <- system.file("extdata", "australia.csv", package="h2o")
#  australia.hex = h2o.importFile(path = ausPath, destination_frame="australia.hex")
  arrestsH2O <- h2o.uploadFile(locate("smalldata/pca_test/USArrests.csv"), destination_frame = "arrestsH2O")
  pca_model = h2o.prcomp(training_frame = arrestsH2O, k = 4, transform = "STANDARDIZE")
  print(pca_model)
  glrm_model = h2o.glrm(training_frame = arrestsH2O, k = 4, loss = "Quadratic", gamma_x = 0,
  gamma_y = 0, max_iterations = 1000, recover_svd=TRUE, init="SVD", transform = "STANDARDIZE")
  print(glrm_model)
  print(glrm_model@model$importance)
  
  # compare the variance metrics between PCA and GLRM to make sure GLRM is generating the right input.
  compare_tables(pca_model@model$importance, glrm_model@model$importance, 1e-4)
  
}

doTest("PUBDEV-3501: add variance metrics for GLRM and Compare with PCA results", test.glrm.pubdev_3501.variance.metrics)
