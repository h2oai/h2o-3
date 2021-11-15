setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This tests tweedie distribution with weight and offset column for GAM.  We will compare results with glm.
testGAMWeightOffset <- function() {
	print("parse data")
	library(MASS) 
	data(Insurance)
	offset = log(Insurance$Holders) 
	weight = offset*0.2
	class(Insurance$Group) <- "factor" 
	class(Insurance$Age) <- "factor" 
	dd = data.frame(Insurance,offset,weight) 
	hdd = as.h2o(dd,destination_frame = "hdd") 
	
	print("build models w/ and w/o offset")
	myX = setdiff(colnames(hdd),c("Claims","offset", "weight"))
	glm_with_weight_offset <- h2o.glm(x = myX,y = "Claims",family = "tweedie",tweedie_variance_power = 1.7,tweedie_link_power = 0,
               lambda = 0,alpha = 0,offset_column = "offset",weights_column="weight", training_frame = hdd, objective_epsilon=0, seed=12345)
	gam_with_weight_offset <- h2o.gam(x = myX,y = "Claims",family = "tweedie",tweedie_variance_power = 1.7,tweedie_link_power = 0,
	                           lambda = 0,alpha = 0,offset_column = "offset",weights_column="weight", training_frame = hdd, 
	                           gam_columns=c("Holders"), objective_epsilon=0, seed=12345)
	print("Compare metrics") # expect GAM metrics better
	expect_true(gam_with_weight_offset@model$training_metrics@metrics$MSE <= glm_with_weight_offset@model$training_metrics@metrics$MSE)
}

doTest("GAM tweedie Test: GAM w/ offset and weights", testGAMWeightOffset)
