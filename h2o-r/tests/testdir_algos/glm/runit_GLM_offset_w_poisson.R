setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests offset in glm on real data ######




test <- function() {
	#read in data
	print("read in data")
	swed = read.table(h2oTest.locate("smalldata/glm_test/Motor_insurance_sweden.txt"),header = T)
	log_insured =log(swed$Insured)
	dd  = data.frame(swed,log_insured)
	dd$Kilometres = as.factor(dd$Kilometres)
	dd$Zone = as.factor(dd$Zone)
	dd$Make = as.factor(dd$Make)
	dd$Bonus = as.factor(dd$Bonus)
	hd = as.h2o(dd,destination_frame = "hd")
	#build models
	print("build models")
	hh = h2o.glm(x = 1:4,y = "Claims",training_frame = hd,family = "poisson",offset_column = "log_insured",lambda = 0)
	no_off_hh = h2o.glm(x = 1:4,y = "Claims",training_frame = hd,family = "poisson",lambda = 0)

	gg = glm(formula = Claims~factor(Kilometres)+factor(Zone)+factor(Make)+factor(Bonus),family = "poisson",
         offset = log(Insured),data = swed)
	no_off_gg = glm(formula = Claims~factor(Kilometres)+factor(Zone)+factor(Make)+factor(Bonus),family = "poisson",
         data = swed)
	print("compare results")
	expect_equal(hh@model$training_metrics@metrics$residual_deviance,gg$deviance,tolerance = 1e-4)
	expect_less_than(hh@model$training_metrics@metrics$residual_deviance, no_off_hh@model$training_metrics@metrics$residual_deviance)
	expect_less_than(hh@model$training_metrics@metrics$null_deviance, no_off_hh@model$training_metrics@metrics$null_deviance)
	print("test ends")
	
}


h2oTest.doTest("GLM offset Test: GLM w/ offset", test)
