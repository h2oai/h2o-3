setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests weights in glm on real data ######




test <- function() {
	#read in data
	print("read in data")
	fre = h2o.importFile(h2oTest.locate("smalldata/glm_test/freMTPL2freq.csv.zip"),destination_frame = "fre")
	fre = fre[1:5000,]
	fre$VehPower = as.factor(fre$VehPower)
	fren = as.data.frame(fre)
	
	#build models
	print("build models")
	gg = glm(formula = ClaimNb~ Area +as.factor(VehPower)+ VehAge+ DrivAge+ BonusMalus+ VehBrand + VehGas +Density +Region,family = "poisson",
                  weights=Exposure,data = fren)
	hh = h2o.glm(x = 4:12,y = "ClaimNb",training_frame = fre,family = "poisson",weights_column = "Exposure",lambda = 0)
	no_weight_hh =  h2o.glm(x = 4:12,y = "ClaimNb",training_frame = fre,family = "poisson",lambda = 0)

	print("compare results")
	expect_equal(hh@model$training_metrics@metrics$residual_deviance,gg$deviance)
	expect_less_than(hh@model$training_metrics@metrics$residual_deviance, no_weight_hh@model$training_metrics@metrics$residual_deviance)
	expect_less_than(hh@model$training_metrics@metrics$null_deviance, no_weight_hh@model$training_metrics@metrics$null_deviance)

	print("test ends")
	
}


h2oTest.doTest("GLM weight Test: GLM w/ weights", test)









