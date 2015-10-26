


test.rdocapply.golden <- function() {
irisPath <- locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(apply(iris.hex, 1, sum))


}

doTest("R Doc Apply", test.rdocapply.golden)

