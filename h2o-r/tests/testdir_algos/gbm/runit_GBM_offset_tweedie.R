setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests tweedie distribution w offset in gbm by comparing results with R ######



test <- function(h) {

	library(MASS) 
	data(Insurance)

#	fit2 = gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,shrinkage = .1,bag.fraction = 1,train.fraction = 1, 
#           data = Insurance, distribution ="tweedie", n.trees = 600) 
#	pr = predict(fit2, Insurance) 
#	pr = exp(pr+log(Insurance$Holders)) 

	offset = log(Insurance$Holders) 
	class(Insurance$Group) <- "factor" 
	class(Insurance$Age) <- "factor" 
	df = data.frame(Insurance,offset) 
	hdf = as.h2o(df,destination_frame = "hdf") 


	hh = h2o.gbm(x = 1:3,y = "Claims",distribution ="tweedie",ntrees = 600,max_depth = 1,min_rows = 1,learn_rate = .1,offset_column = "offset",training_frame = hdf) 
	ph = as.data.frame(h2o.predict(hh,newdata = hdf)) 
#	expect_equal(fit2$initF, hh@model$init_f)
#	expect_equal(mean(pr), mean(ph[,1]),tolerance=1e-5 )
#	expect_equal(min(pr), min(ph[,1]) ,tolerance=1e-5)
#	expect_equal(max(pr), max(ph[,1]) ,tolerance=1e-5)

	expect_equal(-1.869702, hh@model$init_f, tolerance=1e-5)
	expect_equal(49.21591, mean(ph[,1]),tolerance=1e-5 )
	expect_equal(1.0258, min(ph[,1]) ,tolerance=1e-4)
	expect_equal(392.4651, max(ph[,1]) ,tolerance=1e-4)
	
	
}
h2oTest.doTest("GBM offset Test: GBM w/ offset for tweedie distribution", test)
