setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.rdoc_download_csv.golden <- function() {

irisPath <- locate("smalldata/extdata/iris_wheader.csv")
iris.hex <- h2o.uploadFile(path = irisPath)
myFile <- paste(sandbox(), "my_iris_file.csv", sep = .Platform$file.sep)
h2o.downloadCSV(iris.hex, myFile)

}

doTest("R Doc Download CSV", test.rdoc_download_csv.golden)

