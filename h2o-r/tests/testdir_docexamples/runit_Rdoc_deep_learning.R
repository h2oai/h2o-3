
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_deep_learning.golden <- function(H2Oserver) {

irisPath = system.file("extdata", "iris.csv", package = "h2o")
iris.hex = h2o.uploadFile(path = irisPath)
indep <- names(iris.hex)[1:4]
dep <- names(iris.hex)[5]
h2o.deeplearning(x = indep, y = dep, training_frame = iris.hex, activation = "Tanh", epochs = 5, loss = "CrossEntropy")

testEnd()
}

doTest("R Doc Deep Learning", test.rdoc_deep_learning.golden)

