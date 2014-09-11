setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')


rfReg.vi.test<-
function(conn) {

	data2.hex <- h2o.uploadFile(conn, locate("smalldata/BostonHousing.csv"), key="data2.hex")
	x=1:13
	y=14
	rf <- h2o.randomForest(x, y, data2.hex, classification=F, importance=T, ntree=100, depth=20, nbins=100, type = "BigData")
	vi=order(rf@model$varimp[1,],decreasing=T)
	expect_equal(vi[1:2], c(13,6))
	 testEnd()
}
doTest("Variable Importance RF Test: Boston Housing Smalldata", rfReg.vi.test)
