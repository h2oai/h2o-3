setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rdocnfoldextractor.golden <- function(localH2O) {
irisPath <- system.file("extdata", "iris.csv", package = "h2o")
iris.hex <- h2o.uploadFile(localH2O, path = irisPath)
iris.folds <- h2o.nFoldExtractor(iris.hex, nfolds=10, fold_to_extract = 4)
head(iris.folds[[1]])
summary(iris.folds[[1]])
head(iris.folds[[2]])
summary(iris.folds[[2]])

}

doTest("R Doc NfoldExtractor", test.rdocnfoldextractor.golden)

