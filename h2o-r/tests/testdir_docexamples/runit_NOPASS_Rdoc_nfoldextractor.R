setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocnfoldextractor.golden <- function() {
irisPath <- system.file("extdata", "iris.csv", package = "h2o")
iris.hex <- h2o.uploadFile(path = irisPath)
iris.folds <- h2o.nFoldExtractor(iris.hex, nfolds=10, fold_to_extract = 4)
head(iris.folds[[1]])
summary(iris.folds[[1]])
head(iris.folds[[2]])
summary(iris.folds[[2]])

testEnd()
}

doTest("R Doc NfoldExtractor", test.rdocnfoldextractor.golden)

