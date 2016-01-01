setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests offset in gbm for bernoulli by comparing results with R ######




test <- function() {

	set.seed(45541)
	x=matrix(rnorm(100*20),100,20)
	x1=rep(.5,100)
	#y=rnorm(100)
	y = rbinom(100,1,prob = .3)
	df = data.frame(x,x1,y)
	hdf = as.h2o(df,destination_frame = "hdf")
	hdf$y = as.factor(hdf$y)
	gg = gbm(formula = y~.+offset(x1),distribution = "bernoulli",data = df,n.trees = 1,interaction.depth = 1,n.minobsinnode = 1,shrinkage = 1,train.fraction = 1,bag.fraction = 1)
	hh = h2o.gbm(x = 1:20,y = "y",training_frame = hdf,distribution = "bernoulli",ntrees = 1,max_depth = 1,min_rows = 1,learn_rate = 1,offset_column = "x1")
	expect_equal(gg$initF, hh@model$init_f)
	ph = h2o.predict(object = hh,newdata = hdf)
	pr = predict.gbm(object = gg,newdata = df,n.trees = 1,type = "link")
	pr = 1/(1+exp(-df$x1 - pr))
	expect_equal(mean(pr), mean(ph[,3]),tolerance=1e-6 )
	expect_equal(min(pr), min(ph[,3]),tolerance=1e-6 )
	expect_equal(max(pr), max(ph[,3]),tolerance=1e-6 )
	
	
}
h2oTest.doTest("GBM offset Test: GBM w/ offset for bernoulli distribution", test)
