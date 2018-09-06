setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# This tests lift calculation consistency for xval models
# by comparing xval models created using user defined fold column vs model built using weights(0-1) column
# dataset - http://mlr.cs.umass.edu/ml/datasets/Bank+Marketing

test.xval.lift <- function(conn){
	
	a= h2o.importFile(locate("smalldata/gbm_test/bank-full.csv.zip"),destination_frame = "bank")
	dim(a)
	myX = 1:16
	myY = 17

	rowss =45211
	#Sample rows for 2-fold xval
        set.seed(1)
	ss = sample(1:rowss,size = 22000)
	ww = rep(1,rowss)
	ww[ss]=2
	
	#Bring data to R
	ra = as.data.frame(a)
	#Split data into test/train based on fold column
	train = ra[which(ww==1),]
	test = ra[which(ww==2),]
	dim(train) ; dim(test);
	tr = as.h2o(x = train,destination_frame = "train")
	ts = as.h2o(x = test,destination_frame = "test")

	#Parse fold column to h2O
	wei = as.h2o(ww,destination_frame = "weight")
	colnames(wei)
	
	#Cbind fold column to the original dataset
	a = h2o.assign(h2o.cbind(a,wei),key = "bank")
	dim(a)
	
	#Build gbm by specifying the fold column
	gg = h2o.gbm(x = myX,y = myY,training_frame = a,ntrees = 5,fold_column = "x",model_id = "cv_gbm",
				 keep_cross_validation_models = T, keep_cross_validation_predictions = T)

	#Collect the cross-validation models
	cv1 = h2o.getModel("cv_gbm_cv_1")
	cv2 = h2o.getModel("cv_gbm_cv_2")
	
	#Define and use weights column to build equivalent gbm models 
	ww[ss]=0
	wi = as.h2o(ww,destination_frame = "weight_col")
	a = h2o.assign(h2o.cbind(a,wi),key = "bank")
	gg1 = h2o.gbm(x = myX,y = myY,training_frame = a,weights_column = "x0",ntrees = 5,model_id = "gbm1")
	ww = rep(0,rowss)
	ww[ss]=1
	wi = as.h2o(ww,destination_frame = "weight_col")
	a = h2o.assign(h2o.cbind(a,wi),key = "bank")
	gg2 = h2o.gbm(x = myX,y = myY,training_frame = a,weights_column = "x1",ntrees = 5,model_id = "gbm2")
	
	#Compare gain tables for xval vs (weighted) models 
	expect_equal(h2o.gainsLift(cv1,ts),h2o.gainsLift(gg1,ts))
	expect_equal(h2o.gainsLift(cv2,tr),h2o.gainsLift(gg2,tr))
	
}
doTest("Test lift-gain xval",test.xval.lift )
