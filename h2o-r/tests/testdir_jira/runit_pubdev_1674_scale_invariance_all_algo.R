setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
####### This tests scale invariance for the response column in all algos  ######




test <- function() {
	s=c(1e-2,1e2,1e-4,1e4,1e-8,1e8)
	
	print("GBM")
	
	for( i in 1:length(s)){
	x = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	myX = 2:8
	myY = "GLEASON"
	gg = h2o.gbm(x = myX,y = myY,training_frame = x,ntrees = 50,max_depth = 3,learn_rate = 1,min_rows = 1)
	pr = as.data.frame(h2o.predict(gg,newdata = x))
	y =  h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	scale = s[i]
	print(scale)
	y$GLEASON = y$GLEASON/scale
	hh = h2o.gbm(x = myX,y = myY,training_frame = y,ntrees = 100,max_depth = 3,learn_rate = 1,min_rows = 1)
	ph = as.data.frame(h2o.predict(hh,newdata = y))
	scaled_pr = ph[,1]*scale
	print(summary(scaled_pr))
	print(summary(pr))
	expect_equal(mean(pr[,1]), mean(scaled_pr),tolerance = 1e-3 )
	expect_equal(min(pr[,1]), min(scaled_pr) ,tolerance = 2e-1 )
	expect_equal(max(pr[,1]), max(scaled_pr) ,tolerance = 2e-1 )
	}
	
	print("DRF")
	
	for( i in 1:length(s)){
	x =  h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	myX = 2:8
	myY = "GLEASON"
	gg = h2o.randomForest(x=myX,y = myY,training_frame = x,max_depth = 10,seed = 12345)
	pr = as.data.frame(h2o.predict(gg,newdata = x))
	y =  h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	scale = s[i]
	print(scale)
	y$GLEASON = y$GLEASON/scale
	hh = h2o.randomForest(x=myX,y = myY,training_frame = y,max_depth = 10,seed = 12345)
	ph = as.data.frame(h2o.predict(hh,newdata = y))
	scaled_pr = ph[,1]*scale
	print(summary(scaled_pr))
	print(summary(pr))
	expect_equal(mean(pr[,1]), mean(scaled_pr),tolerance = 1e-2 )
	expect_equal(min(pr[,1]), min(scaled_pr) ,tolerance = 1e-1 )
	expect_equal(max(pr[,1]), max(scaled_pr) ,tolerance = 1e-2 )
	}


	print("GLM")

	for( i in 1:length(s)){
	x =  h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	myX = 2:8
	myY = "GLEASON"
	gg = h2o.glm(x = myX,y = myY,training_frame = x,lambda=0)
	pr = as.data.frame(h2o.predict(gg,newdata = x))
	y = h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	scale = s[i]
	print(scale)
	y$GLEASON = y$GLEASON/scale
	hh = h2o.glm(x = myX,y = myY,training_frame = y,lambda=0)
	ph = as.data.frame(h2o.predict(hh,newdata = y))
	scaled_pr = ph[,1]*scale
	print(summary(scaled_pr))
	print(summary(pr))
	expect_equal(mean(pr[,1]), mean(scaled_pr),tolerance = 1e-7 )
	expect_equal(min(pr[,1]), min(scaled_pr) ,tolerance = 1e-7 )
	expect_equal(max(pr[,1]), max(scaled_pr) ,tolerance = 1e-7 )
	}

	print("DL")

	for( i in 1:length(s)){
	x =  h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	myX = 2:8
	myY = "GLEASON"
	gg = h2o.deeplearning(x = myX,y = myY,training_frame = x,hidden = c(10,10),epochs = 100,activation = "Tanh",seed = 12345,reproducible = T)
	pr = as.data.frame(h2o.predict(gg,newdata = x))
	y =  h2o.uploadFile(h2oTest.locate("smalldata/logreg/prostate.csv"))
	scale = s[i]
	print(scale)
	y$GLEASON = y$GLEASON/scale
	hh = h2o.deeplearning(x = myX,y = myY,training_frame = y,hidden = c(10,10),epochs = 100,activation = "Tanh",seed = 12345,reproducible = T)
	ph = as.data.frame(h2o.predict(hh,newdata = y))
	scaled_pr = ph[,1]*scale
	print(summary(scaled_pr))
	print(summary(pr))
	expect_equal(mean(pr[,1]), mean(scaled_pr),tolerance = 2e-2 )
	expect_equal(min(pr[,1]), min(scaled_pr) ,tolerance = 6e-1 )
	expect_equal(max(pr[,1]), max(scaled_pr) ,tolerance = 6e-1 )
	}
	
	
}
h2oTest.doTest("Scale Invariance Test: for all algos", test)



