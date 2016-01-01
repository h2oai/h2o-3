setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.predict.withoutresponse <- function(h) {

	ir = h2o.uploadFile(normalizePath(h2oTest.locate("smalldata/iris/iris.csv")),destination_frame="ir")
	ss = h2o.splitFrame(data=ir, ratios=.2, seed = 0)

	train = ss[[2]] 
	expect_equal(nrow(train),117)
	test = ss[[1]] 
	test = test[,-5] 
	expect_equal(ncol(test),4) 
	
	gg= h2o.gbm(x=1:4,y = 5,training_frame=train) 
	pr = h2o.predict(object=gg,newdata=test) 

}

h2oTest.doTest("Test predicts on data without response for multiclass", test.predict.withoutresponse)
