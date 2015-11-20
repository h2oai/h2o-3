


test.rdoccolnames.golden <- function() {


irisPath <- locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(iris.hex)
colnames(iris.hex)


}

doTest("R Doc Col Names", test.rdoccolnames.golden)
