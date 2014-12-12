    
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_deep_learning.golden <- function(H2Oserver) {
	
irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.importFile(H2Oserver, path = irisPath)
h2o.deeplearning(x = 1:4, y = 5, training_frame = iris.hex, activation = "Tanh", rate = 0.05)

testEnd()
}

doTest("R Doc Deep Learning", test.rdoc_deep_learning.golden)

