setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
####### This tests offset in deeplearing for gamma by comparing results with expected behavior ######




test <- function(h) {
	library(MASS) 
	data(Insurance)

	#gg = gbm(Claims ~ District + Group + Age+ offset(log(Holders)) , verbose=T,interaction.depth = 1,n.minobsinnode = 1,shrinkage = .1,bag.fraction = 1,train.fraction = 1, 
	#           data = Insurance, distribution ="gamma", n.trees = 600) 
	#gg$train.error  #8.2989 
	#pr = predict(gg, Insurance) 
	#pr = exp(pr+log(Insurance$Holders)) 
	#summary(pr)   #mean = 50.1100; min = 0.9134; max = 392.7000 ; 
	offset = log(Insurance$Holders) 
	class(Insurance$Group) <- "factor" 
	class(Insurance$Age) <- "factor" 
	df = data.frame(Insurance,offset) 
	hdf = as.h2o(df,destination_frame = "hdf") 
	hdf$Claims <- hdf$Claims+1e-19
	hh = h2o.deeplearning(x = 1:3,y = "Claims",distribution ="gamma",hidden = c(1),epochs = 1000,train_samples_per_iteration = -1,
                      reproducible = T,activation = "Tanh",balance_classes = F,force_load_balance = F,
                      seed = 516736545500,tweedie_power = 1.5,score_training_samples = 0,score_validation_samples = 0,
                      offset_column = "offset",training_frame = hdf,validation_frame=hdf) 
	mean_deviance = hh@model$training_metrics@metrics$mean_residual_deviance
	expect_equal(hh@model$training_metrics@metrics$mean_residual_deviance,hh@model$validation_metrics@metrics$mean_residual_deviance)
	ph = as.data.frame(h2o.predict(hh,newdata = hdf))
	print(mean_deviance)
	print(mean(ph[,1]))
	print(min(ph[,1]))
	print(max(ph[,1]))
	expect_equal(mean_deviance,8.3, tolerance=1e-2)
	expect_equal(50.7, mean(ph[,1]), tolerance=1e-2 )
	expect_equal(0.941, min(ph[,1]), tolerance=1e-2 )
	expect_equal(415, max(ph[,1]), tolerance=1e-2 )

	
}
h2oTest.doTest("Deeplearning offset Test: deeplearning w/ offset for gamma distribution", test)

