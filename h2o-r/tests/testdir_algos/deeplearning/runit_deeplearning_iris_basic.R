setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.deeplearning_basic <- function(conn) {
	iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), "iris.hex")
	iris.hex[,5] <- as.factor(iris.hex[,5])
	hh <- h2o.deeplearning(x=c(1,2,3,4),y=5,training_frame=iris.hex,loss="CrossEntropy")
	print(hh)
  testEnd()
}

doTest("Deep Learning Test: Iris", check.deeplearning_basic)

