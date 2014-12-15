setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_screeplot.golden <- function(H2Oserver) {

ausPath <- system.file("extdata", "australia.csv", package = "h2o")
australia.hex <- h2o.importFile(H2Oserver, path = ausPath)
australia.pca <- h2o.prcomp(training_frame = australia.hex, standardize = TRUE)
screeplot(australia.pca)

testEnd()
}

doTest("R Doc screeplot", test.rdoc_screeplot.golden)

