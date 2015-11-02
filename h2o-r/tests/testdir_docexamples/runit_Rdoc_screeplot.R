


test.rdoc_screeplot.golden <- function() {
  ausPath <- locate("smalldata/extdata/australia.csv")
  australia.hex <- h2o.uploadFile(path = ausPath)
  australia.pca <- h2o.prcomp(training_frame = australia.hex, k = 4, transform = "STANDARDIZE")
  screeplot(australia.pca)

  
}

doTest("R Doc screeplot", test.rdoc_screeplot.golden)

