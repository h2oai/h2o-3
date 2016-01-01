setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.principalcomp.golden <- function() {
  #Example from prcomp R doc

  ausPath <- h2oTest.locate("smalldata/extdata/australia.csv")
  australia.hex <- h2o.uploadFile(path = ausPath)
  australia.pca <- h2o.prcomp(training_frame = australia.hex, k = 8, transform = "STANDARDIZE")
  model <- print(australia.pca)
  summary <- summary(australia.pca)

  
}

h2oTest.doTest("R Doc Principal Components Regression Ex", test.principalcomp.golden)
