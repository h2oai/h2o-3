


test.rdoc_nrow_ncol.golden <- function() {

irisPath <- locate("smalldata/extdata/iris.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
nrow(iris.hex)
ncol(iris.hex)


}

doTest("R Doc nrow and ncol", test.rdoc_nrow_ncol.golden)
