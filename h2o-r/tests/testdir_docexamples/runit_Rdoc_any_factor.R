


test.anyFactor <- function() {

 irisPath <- locate("smalldata/extdata/iris_wheader.csv")
 iris.hex <- h2o.uploadFile( path = irisPath)
 h2o.anyFactor(iris.hex)


}

doTest("R Doc h2o.anyFactor", test.anyFactor)
