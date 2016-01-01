setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests offset in gbm for poisson by comparing results with R ######




test <- function(h) {
	
	library(gbm)
	library(MASS) 
	data(Insurance)
	fit2 = gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , interaction.depth = 1,n.minobsinnode = 1,shrinkage = .1,bag.fraction = 1,train.fraction = 1,
            	data = Insurance, distribution ="poisson", n.trees = 600) 
	link = predict.gbm(fit2, Insurance, n.trees=600, type="link")
	link.offset = link + log(Insurance$Holders)
	#for poisson
	pr = exp(link.offset)
	offset = log(Insurance$Holders)
	class(Insurance$Group) <- "factor"
	class(Insurance$Age) <- "factor"
	df = data.frame(Insurance,offset)
	hdf = as.h2o(df,destination_frame = "hdf")
	hh = h2o.gbm(x = 1:3,y = "Claims",distribution ="poisson",ntrees = 600,max_depth = 1,min_rows = 1,learn_rate = .1,offset_column = "offset",training_frame = hdf)
	ph = as.data.frame(h2o.predict(hh,newdata = hdf))
	expect_equal(fit2$initF, hh@model$init_f)
	#expect_equal( fit2$train.error[600], hh@model$training_metrics@metrics$MSE,tolerance=1e-6)
	expect_equal(mean(pr), mean(ph[,1]),tolerance=1e-5 )
	expect_equal(min(pr), min(ph[,1]) ,tolerance=1e-4)
	expect_equal(fit2$train.error[length(fit2$train.error)], hh@model$training_metrics@metrics$mean_residual_deviance,tolerance=1e-4) #residual deviance
	expect_equal(max(pr), max(ph[,1]) ,tolerance=1e-4)
	
	
}
h2oTest.doTest("GBM offset Test: GBM w/ offset for poisson distribution", test)
