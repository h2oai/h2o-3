setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests tweedie distribution,tweedie with offsets,  and tweedie with weights in glm ######




test <- function() {
	print("parse data")
	dh = h2o.uploadFile(h2oTest.locate("smalldata/glm_test/freMTPL2freq.csv.zip"),destination_frame="dh")
	colnames(dh)
	
	print("build models w/ and w/o weights")
	myX = setdiff(colnames(dh),c("IDpol","ClaimNb","Exposure"))
	hh_with_weights <- h2o.glm(x = myX,y = "ClaimNb",family = "tweedie",tweedie_variance_power = 1.05,
                weights_column = "Exposure",tweedie_link_power = 0,lambda = 0,alpha = 0,training_frame = dh)
	hh_no_weights <- h2o.glm(x = myX,y = "ClaimNb",family = "tweedie",tweedie_variance_power = 1.05,
                tweedie_link_power = 0,lambda = 0,alpha = 0,training_frame = dh)

	# From glm in R
        dd = as.data.frame(dh)
        gg_no_weights   <- glm(ClaimNb~.-Exposure-IDpol, family=tweedie(var.power=1.05,link.power=0), data = dd)
        gg_with_weights <- glm(ClaimNb~.-Exposure-IDpol, family=tweedie(var.power=1.05,link.power=0), data = dd,weights=Exposure)
	
	expect_equal(gg_with_weights$null.deviance, hh_with_weights@model$training_metrics@metrics$null_deviance)
	expect_equal(deviance(gg_with_weights),hh_with_weights@model$training_metrics@metrics$residual_deviance)

	expect_equal(gg_no_weights$null.deviance, hh_no_weights@model$training_metrics@metrics$null_deviance)
	expect_equal(deviance(gg_no_weights),hh_no_weights@model$training_metrics@metrics$residual_deviance)

	ph = as.data.frame(h2o.predict(hh_with_weights,newdata = dh)) 
	pr = predict(gg_with_weights,newdata = dd,type = "response") 
	expect_equal(mean(ph[,1]),mean(pr),tolerance=1e-5)

	print("parse data")
	library(MASS) 
	data(Insurance)
	offset = log(Insurance$Holders) 
	class(Insurance$Group) <- "factor" 
	class(Insurance$Age) <- "factor" 
	dd = data.frame(Insurance,offset) 
	hdd = as.h2o(dd,destination_frame = "hdd") 
	
	print("build models w/ and w/o offset")
	myX = setdiff(colnames(hdd),c("Claims","offset"))
	hh_with_offset <- h2o.glm(x = myX,y = "Claims",family = "tweedie",tweedie_variance_power = 1.7,tweedie_link_power = 0,
               lambda = 0,alpha = 0,offset_column = "offset",training_frame = hdd)
	gg_with_offset <- glm(Claims~.- offset + offset(offset),family=tweedie(var.power=1.7,link.power=0),data = dd)

	hh_no_offset <- h2o.glm(x = myX,y = "Claims",family = "tweedie",tweedie_variance_power = 1.7,tweedie_link_power = 0,
               lambda = 0,alpha = 0,training_frame = hdd)
	gg_no_offset <- glm(Claims~.-offset,family=tweedie(var.power=1.7,link.power=0),data = dd)

	print("Check deviance and predictions")
	expect_equal(gg_with_offset$null.deviance, hh_with_offset@model$training_metrics@metrics$null_deviance)
	expect_equal(deviance(gg_with_offset),hh_with_offset@model$training_metrics@metrics$residual_deviance)

	expect_equal(gg_no_offset$null.deviance, hh_no_offset@model$training_metrics@metrics$null_deviance)
	expect_equal(deviance(gg_no_offset),hh_no_offset@model$training_metrics@metrics$residual_deviance)


	ph = as.data.frame(h2o.predict(hh_with_offset,newdata = hdd)) 
	pr = predict(gg_with_offset,newdata = dd,type = "response")
	expect_equal(mean(ph[,1]),mean(pr),tolerance =1e-4)

	
}


h2oTest.doTest("GLM tweedie Test: GLM w/ offset and weights", test)
