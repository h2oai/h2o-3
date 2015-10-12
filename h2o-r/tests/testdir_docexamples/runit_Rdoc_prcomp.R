setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.principalcomp.golden <- function() {
  #Example from prcomp R doc

  ausPath <- locate("smalldata/extdata/australia.csv")
  australia.hex <- h2o.uploadFile(path = ausPath)
  australia.pca <- h2o.prcomp(training_frame = australia.hex, k = 8, transform = "STANDARDIZE")
  model <- print(australia.pca)
  summary <- summary(australia.pca)

  
}

doTest("R Doc Principal Components Regression Ex", test.principalcomp.golden)
