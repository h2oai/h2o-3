####### This tests offset in deeplearning for tweedie by comparing results with expected behaviour  ######

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function() {
	
	#library(gbm)
	library(MASS) 
	data(Insurance)
	offset = log(Insurance$Holders) 
	class(Insurance$Group) <- "factor" 
	class(Insurance$Age) <- "factor" 
	df = data.frame(Insurance,offset) 
	hdf = as.h2o(df,destination_frame = "hdf") 
	
	# Expect deviance to improve when run with offset column for this dataset
	
	# without offset
	#hh = h2o.gbm(x = 1:3,y = "Claims",distribution ="tweedie",ntrees = 100,tweedie_power = 1.5,
     #        max_depth = 1,min_rows = 1,learn_rate = .1,training_frame = hdf,) 
	#hh@model$training_metrics@metrics$mean_residual_deviance
	#[1] 0.5716951
	#pr = as.data.frame(h2o.predict(hh,newdata = hdf))  mean(pr) = 47.33365; max(pr) = 361.0456; min(pr) = 3.017074
	
	hh = h2o.deeplearning(x = 1:3,y = "Claims",distribution ="tweedie",hidden = c(1),epochs = 1000,train_samples_per_iteration = -1,
                      reproducible = T,activation = "Tanh",single_node_mode = F,balance_classes = F,force_load_balance = F,
                      seed = 23123,tweedie_power = 1.5,score_training_samples = 0,score_validation_samples = 0,
                      training_frame = hdf) 
	mean_deviance = hh@model$training_metrics@metrics$mean_residual_deviance
	expect_equal(0.5573887,mean_deviance)
	ph = as.data.frame(h2o.predict(hh,newdata = hdf)) 
	expect_equal(47.80020346, mean(ph[,1]) )
	expect_equal(2.017399703, min(ph[,1]) )
	expect_equal(285.0190279, max(ph[,1]) )
	
	# with offset
	#hh = h2o.gbm(x = 1:3,y = "Claims",distribution ="tweedie",ntrees = 100,tweedie_power = 1.5,
     #        max_depth = 1,min_rows = 1,learn_rate = .1,training_frame = hdf,offset_column = "offset") 
	#hh@model$training_metrics@metrics$mean_residual_deviance
	#pr = as.data.frame(h2o.predict(hh,newdata = hdf)) mean(pr) = 49.64749; max(pr) = 402.4205; min(pr) = 0.9327381
	
	hh = h2o.deeplearning(x = 1:3,y = "Claims",distribution ="tweedie",hidden = c(1),epochs = 1000,train_samples_per_iteration = -1,
                      reproducible = T,activation = "Tanh",single_node_mode = F,balance_classes = F,force_load_balance = F,
                      seed = 23123,tweedie_power = 1.5,score_training_samples = 0,score_validation_samples = 0,
                      offset_column = "offset",training_frame = hdf) 
	
	mean_deviance = hh@model$training_metrics@metrics$mean_residual_deviance
	expect_equal(0.2606853029,mean_deviance)
	ph = as.data.frame(h2o.predict(hh,newdata = hdf)) 
	expect_equal(49.28646452, mean(ph[,1]) )
	expect_equal(1.068305274, min(ph[,1]) )
	expect_equal(397.0244326, max(ph[,1]) )
	
	testEnd()
}
doTest("Deeplearning offset Test: deeplearning w/ offset for tweedie distribution", test)

	
