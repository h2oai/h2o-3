##
# Comparison of H2O to R with varying link functions for the TWEEDIE family on prostate dataset
# Link functions: tweedie (canonical link)
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test.linkFunctions <- function(conn) {

	print("Read in prostate data.")
	h2o.data = h2o.uploadFile(conn, locate("smalldata/prostate/prostate_complete.csv.zip"), key="h2o.data")    
	R.data = as.data.frame(as.matrix(h2o.data))
	
	print("Testing for family: TWEEDIE")
	print("Set variables for h2o.")
	myY = "CAPSULE"
	myX = c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
	print("Define formula for R")
	R.formula = (R.data[,"CAPSULE"]~.) 

	print("Create models with canonical link: TWEEDIE")
	model.h2o.tweedie.tweedie <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="tweedie", link="tweedie",alpha=0.5, lambda=0, n_folds=0)
	model.R.tweedie.tweedie <- glm(formula=R.formula, data=R.data[,4:10], family=tweedie, na.action=na.omit)

	print("Compare model deviances for link function tweedie")
	deviance.h2o.tweedie = model.h2o.tweedie.tweedie@model$residual_deviance / model.h2o.tweedie.tweedie@model$null_deviance
	deviance.R.tweedie = deviance(model.R.tweedie.tweedie)  / model.h2o.tweedie.tweedie@model$null_deviance
	difference = deviance.R.tweedie - deviance.h2o.tweedie
	if (difference > 0.01) {
		print(cat("Deviance in H2O: ", deviance.h2o.tweedie))
		print(cat("Deviance in R: ", deviance.R.tweedie))
		checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	}

testEnd()
}

doTest("Comparison of H2O to R with varying link functions for the TWEEDIE family", test.linkFunctions)


