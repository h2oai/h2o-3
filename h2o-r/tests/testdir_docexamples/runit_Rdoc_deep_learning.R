



test.rdoc_deep_learning.golden <- function() {

irisPath = locate("smalldata/extdata/iris.csv")
iris.hex = h2o.uploadFile(path = irisPath)
indep <- names(iris.hex)[1:4]
dep <- names(iris.hex)[5]
h2o.deeplearning(x = indep, y = dep, training_frame = iris.hex, activation = "Tanh", epochs = 5, loss = "CrossEntropy")


}

doTest("R Doc Deep Learning", test.rdoc_deep_learning.golden)

