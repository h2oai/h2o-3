
### This tests tweedie distribution,tweedie with offsets,  and tweedie with weights in glm ######

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test <- function() {
	print("parse data")
	dh = h2o.uploadFile(locate("smalldata/glm_test/freMTPL2freq.csv.zip"),destination_frame="dh")
	colnames(dh)
	
	print("build models w/ and w/o weights")
	myX = setdiff(colnames(dh),c("IDpol","ClaimNb","Exposure"))
	hh_with_weights <- h2o.glm(x = myX,y = "ClaimNb",family = "tweedie",tweedie_variance_power = 1.05,
                weights_column = "Exposure",tweedie_link_power = 0,lambda = 0,alpha = 0,training_frame = dh)
	hh_no_weights <- h2o.glm(x = myX,y = "ClaimNb",family = "tweedie",tweedie_variance_power = 1.05,
                tweedie_link_power = 0,lambda = 0,alpha = 0,training_frame = dh)

	# From glm in R
	#dd = as.data.frame(dh)
	#(gg_no_weights<-glm(ClaimNb~.-Exposure-IDpol,family=tweedie(var.power=1.05,link.power=0), data = dd)) #deviance(gg) = 241268.2585101248 ; gg$null.deviance = 246200.2912844299
	#(gg_with_weights<-glm(ClaimNb~.-Exposure-IDpol,family=tweedie(var.power=1.05,link.power=0),
	#					data = dd,weights=Exposure)) #deviance(gg) = 142814.2349666518; gg$null.deviance = 147048.37352688
	
	print("Check deviance and predictions")
	expect_equal(147048.37352688, hh_with_weights@model$training_metrics@metrics$null_deviance)
	expect_equal(142814.2349666518,hh_with_weights@model$training_metrics@metrics$residual_deviance)

	expect_equal(246200.2912844299, hh_no_weights@model$training_metrics@metrics$null_deviance)
	expect_equal(241268.2585101248,hh_no_weights@model$training_metrics@metrics$residual_deviance)

	ph = as.data.frame(h2o.predict(hh_with_weights,newdata = dh)) 
	#pr = predict(gg_with_offset,newdata = dd,type = "response") # mean(pr) = 0.06598871946540595
	expect_equal(mean(ph[,1]),0.06598871946540595,tolerance=1e-8)

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


doTest("GLM tweedie Test: GLM w/ offset and weights", test)