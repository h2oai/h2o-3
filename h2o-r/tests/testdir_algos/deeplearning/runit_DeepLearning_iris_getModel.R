setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

check.deeplearning_basic <- function(conn) {
	iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), "iris.hex")
	hh=h2o.deeplearning(x=c(1,2,3,4),y=5,data=iris.hex)
	print(hh)

  h2o.predict(hh, iris.hex)
  m <- h2o.getModel(conn, hh@key)
  h2o.predict(m, iris.hex)
  testEnd()
}

doTest("Deep Learning Test: Iris", check.deeplearning_basic)

