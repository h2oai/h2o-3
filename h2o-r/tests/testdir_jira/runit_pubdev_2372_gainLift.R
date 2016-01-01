setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# This tests lift-gain chart implementation by sanity checking and comparing results with calculations done in R
# dataset - http://mlr.cs.umass.edu/ml/datasets/Bank+Marketing

test.pubdev.2372 <- function(conn){
	a= h2o.importFile(h2oTest.locate("smalldata/gbm_test/bank-full.csv.zip"),destination_frame = "bank_UCI")
	frm = h2o.splitFrame(data = a,ratios = .7,destination_frames = c("train","test"),seed = 123)
	train = frm[[1]]
	test = frm[[2]]
	dim(train)
	dim(test)
	myX = 1:16
	myY = 17
	print(" Build model")
	model = h2o.gbm(x = myX,y = myY,training_frame = "train")

	print("Get gain table from training data model metric")
	gain_table = model@model$training_metrics@metrics$gains_lift_table
	print(" Plot data fraction vs capture rate")
	plot(gain_table$cumulative_data_fraction,gain_table$cumulative_capture_rate,'l')
	plot(gain_table$cumulative_data_fraction, gain_table$lift, 'l')

	print("get predictions on train set for sanity checks")
	pred = h2o.predict(model,newdata = train)
	pred_prob = as.data.frame(pred[,3])

	print("Sanity checks")
	expect_equal(h2o.table(train[,myY])[2,2]/dim(train)[1],min(gain_table$cumulative_response_rate))
	expect_equal(1,max(gain_table$cumulative_data_fraction))
	expect_equal(1,max(gain_table$cumulative_capture_rate))
	expect_equal(1,min(gain_table$cumulative_lift))
	expect_equal(0,min(gain_table$cumulative_gain))
	expect_equal(gain_table$response_rate/min(gain_table$cumulative_response_rate),gain_table$lift)

	probs = c(0,0.1,0.2,0.3,0.4,0.5,0.6,0.7,0.8,0.85,0.9,0.95,0.96,0.97,0.98,0.99)
	
	h2o_quantile = as.numeric(h2o.quantile(pred[,3],probs = probs))
	gain_prob = sort(gain_table$lower_threshold)
#  print(h2o_quantile)
#  print(gain_prob)
	expect_equal(h2o_quantile,gain_prob,tolerance= 1e-8)

	R_quantile = as.numeric(quantile(pred_prob[,1],probs = probs))
#  print(R_quantile)
#  print(h2o_quantile)
	expect_equal(R_quantile,h2o_quantile,tolerance= 1e-8)

	print("Get gain table from performance metric on test set")
	perf = h2o.performance(model, test)
	expect_equal(h2o.table(test[,myY])[2,2]/dim(test)[1],min(perf@metrics$gains_lift_table$cumulative_response_rate))

	print("Get gain table using h2o.gainsLift function")
	gl_table = h2o.gainsLift(model,test)
	gl_table2 = h2o.gainsLift(perf)
	expect_equal(gl_table,gl_table2)

	print("Compare results when calculation done in R")
	lab = as.data.frame(train[,myY])
	# Sort probabitites 
	idx = sort(pred_prob[,1],decreasing = T,index.return=T)
	lab = lab[idx$ix,1]
	pred_prob = pred_prob[idx$ix,1]

	j = 1
	for(i in length(probs):1){
  	if(i==length(probs)){
    	subs = lab[which(pred_prob >=R_quantile[i])]
  	}else{
    	subs = lab[which(pred_prob< R_quantile[(i+1)] & pred_prob >=R_quantile[i])]
  	}
  	ee = length(which(subs=="yes"))
  	nn = length(subs)
  	pp = ee/nn
  	expect_equal(pp,gain_table$response_rate[j],tolerance= 1e-3)
  	j = j+1
	}

}
h2oTest.doTest("Test lift-gain chart PUBDEV-2372", test.pubdev.2372)
