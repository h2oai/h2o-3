setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.rdoc_cut.golden <- function() {

irisPath <- h2oTest.locate("smalldata/extdata/iris_wheader.csv")
iris.hex <- h2o.uploadFile(path = irisPath, destination_frame = "iris.hex")
summary(iris.hex)
sepal_len.cut <- cut.H2OFrame(iris.hex$sepal_len, c(4.2, 4.8, 5.8, 6, 8))
head(sepal_len.cut)
summary(sepal_len.cut)


}

h2oTest.doTest("R Doc Cut Status", test.rdoc_cut.golden)

