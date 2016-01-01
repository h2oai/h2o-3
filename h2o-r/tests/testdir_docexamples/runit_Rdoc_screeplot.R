setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_screeplot.golden <- function() {
  ausPath <- h2oTest.locate("smalldata/extdata/australia.csv")
  australia.hex <- h2o.uploadFile(path = ausPath)
  australia.pca <- h2o.prcomp(training_frame = australia.hex, k = 4, transform = "STANDARDIZE")
  screeplot(australia.pca)

  
}

h2oTest.doTest("R Doc screeplot", test.rdoc_screeplot.golden)

