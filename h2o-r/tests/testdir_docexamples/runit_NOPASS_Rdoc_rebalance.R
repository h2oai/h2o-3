setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdocrebalance.golden <- function(localH2O) {
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(localH2O, path = irisPath)
iris.reb = h2o.rebalance(iris.hex, chunks = 32)
summary(iris.reb)
iris.reb2 = h2o.rebalance(iris.hex, chunks = 32, key="iris.hex.rebalanced")
summary(iris.reb2)

testEnd()
}

doTest("R Doc ReBalance", test.rdocrebalance.golden)

