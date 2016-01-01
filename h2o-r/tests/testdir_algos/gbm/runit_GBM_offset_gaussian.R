setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests offset in gbm for gaussian by comparing results with R ######




test <- function(h) {
	
	library(gbm)
	library(MASS) 
	data(Insurance)
	fit2 <- gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,shrinkage = .1,bag.fraction = 1,train.fraction = 1,
            	data = Insurance, distribution ="gaussian", n.trees = 600) 
	pg = predict(fit2, newdata = Insurance, type = "response", n.trees=600)
	pr = pg - - log(Insurance$Holders)
	ofset = log(Insurance$Holders)
	class(Insurance$Group) <- "factor"
	class(Insurance$Age) <- "factor"
	df = data.frame(Insurance,ofset)
	hdf = as.h2o(df,destination_frame = "hdf")
	hh = h2o.gbm(x = 1:3,y = "Claims",ntrees = 600,max_depth = 1,min_rows = 1,learn_rate = .1,offset_column = "ofset",training_frame = hdf)
	ph = as.data.frame(h2o.predict(hh,newdata = hdf))
	expect_equal(fit2$initF, hh@model$init_f)
	expect_equal( fit2$train.error[600], hh@model$training_metrics@metrics$MSE,tolerance=1e-6)
	expect_equal(mean(pr), mean(ph[,1]),tolerance=1e-5 )
	expect_equal(min(pr), min(ph[,1]) ,tolerance=1e-3)
	expect_equal(max(pr), max(ph[,1]) ,tolerance=1e-3)
	
}
h2oTest.doTest("GBM offset Test: GBM w/ offset insurance data", test)
