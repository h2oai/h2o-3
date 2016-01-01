setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests offsets in glm ######



test <- function() {

	#create  data
	print("create synthetic data")
	set.seed(40)
	N=500; p=30
	x=matrix(rnorm(N*p),N,p)
	y=rbinom(N, 1,.4)
	off = rnorm(N,0,5)
	rfm = data.frame(y,x,off)
	frm =as.h2o(rfm,destination_frame = "frm")
	set.seed(22)
	off = rnorm(N,-1,1)
	summary(off)
	valid = data.frame(y,x,off)
	val = as.h2o(valid,destination_frame = "val")

	#build model with lambda = 0
	print("build models with offset in h2o and R with lambda=0")
	hh = h2o.glm(x = 2:31,y = 1,training_frame = frm,family = "binomial",offset_column =  "off",lambda = 0)
	gr = glm(formula = y~X1+X2 + X3 +X4 +X5+X6+X7+X8+X9+X10+ X11+X12+X13+X14+X15+X16+X17+X18+X19+ X20+X21+X22+X23+X24+X25+X26+X27+X28+X29+X30,
         family = "binomial",data = rfm,offset= rfm[,32]) 
	gg = glmnet(x = as.matrix(rfm[,-c(1,32)]),y = as.factor(rfm[,1]),family = "binomial",lambda =0,offse = rfm[,32])        
	print("compare results")
	expect_equal(gr$null.deviance, hh@model$training_metrics@metrics$null_deviance)
	expect_equal(gr$aic, hh@model$training_metrics@metrics$AIC,tolerance = 0.00001)
	expect_equal(gr$deviance,hh@model$training_metrics@metrics$residual_deviance,tolerance = 0.00001)
	expect_equal(gr$df.residual,hh@model$training_metrics@metrics$residual_degrees_of_freedom)
	#predictions
	ph = h2o.predict(object = hh,newdata = val)
	pr = predict(object = gg,newx = as.matrix(valid[,-c(1,32)]),offset = as.matrix(valid[,32]),type = "response")
	print("compare predictions")
	expect_equal(min(pr),min(ph$p1),tolerance = 0.0001)
	expect_equal(max(pr),max(ph$p1),tolerance = 0.0001)
	expect_equal(mean(pr),mean(ph$p1),tolerance = 0.0001)

	#build model with lambda != 0
	print("build models with offset in h2o and R with lambda!=0")
	hh = h2o.glm(x = 2:31,y = 1,training_frame = frm,family = "binomial",offset_column =  "off",alpha = 1, lambda = 7.258E-5)
	gg = glmnet(x = as.matrix(rfm[,-c(1,32)]),y = as.factor(rfm[,1]),family = "binomial",alpha = 1,lambda = 7.258E-5,offset = rfm[,32],)
	print("compare results")
	expect_equal(as.vector(hh@model$coefficients[-1]),as.vector(gg$beta),tolerance=.001)
	expect_equal(deviance(gg),hh@model$training_metrics@metrics$residual_deviance,tolerance = 0.00001)
	#predictions
	ph = h2o.predict(object = hh,newdata = val)
	pr = predict(object = gg,newx = as.matrix(valid[,-c(1,32)]),offset = as.matrix(valid[,32]),type = "response")
	print("compare predictions")
	expect_equal(min(pr),min(ph$p1),tolerance = 0.0001)
	expect_equal(max(pr),max(ph$p1),tolerance = 0.0001)
	expect_equal(mean(pr),mean(ph$p1),tolerance = 0.0001)

	
}


h2oTest.doTest("GLM offset Test: GLM w/ offset", test)
