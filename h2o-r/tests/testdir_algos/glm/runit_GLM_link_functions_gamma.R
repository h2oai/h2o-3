setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Comparison of H2O to R with varying link functions for the GAMMA family on prostate dataset
# Link functions: inverse (canonical link)
#				  log
#				  identity
##





test.linkFunctions <- function() {

	print("Read in prostate data.")
	h2o.data = h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")    
	R.data = as.data.frame(as.matrix(h2o.data))
	
	print("Testing for family: GAMMA")
	print("Set variables for h2o.")
	myY = "DPROS"
	myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","GLEASON")
	print("Define formula for R")
	R.formula = (R.data[,"DPROS"]~.) 

	print("Create models with canonical link: INVERSE")
	model.h2o.gamma.inverse <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="gamma", link="inverse",alpha=0.5, lambda=0, nfolds=0)
	model.R.gamma.inverse <- glm(formula=R.formula, data=R.data[,c(1:5,7:9)], family=Gamma(link=inverse), na.action=na.omit)
	
	print("Compare model deviances for link function inverse")
	deviance.h2o.inverse = model.h2o.gamma.inverse@model$training_metrics@metrics$residual_deviance / model.h2o.gamma.inverse@model$training_metrics@metrics$null_deviance
	deviance.R.inverse = deviance(model.R.gamma.inverse)  / model.h2o.gamma.inverse@model$training_metrics@metrics$null_deviance
	difference = deviance.R.inverse - deviance.h2o.inverse
	if (difference > 0.01) {
		print(cat("Deviance in H2O: ", deviance.h2o.inverse))
		print(cat("Deviance in R: ", deviance.R.inverse))
		checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	}

	print("Create models with link function: LOG")
	model.h2o.gamma.log <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="gamma", link="log",alpha=0.5, lambda=0, nfolds=0)
	model.R.gamma.log <- glm(formula=R.formula, data=R.data[,c(1:5,7:9)], family=Gamma(link=log), na.action=na.omit)
	
	print("Compare model deviances for link function log")
	deviance.h2o.log = model.h2o.gamma.log@model$training_metrics@metrics$residual_deviance / model.h2o.gamma.log@model$training_metrics@metrics$null_deviance
	deviance.R.log = deviance(model.R.gamma.log)  / model.h2o.gamma.log@model$training_metrics@metrics$null_deviance
	difference = deviance.R.log - deviance.h2o.log
	if (difference > 0.01) {
		print(cat("Deviance in H2O: ", deviance.h2o.log))
		print(cat("Deviance in R: ", deviance.R.log))
		checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	}

	print("Create models with link: IDENTITY")
	model.h2o.gamma.identity <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="gamma", link="identity",alpha=0.5, lambda=0, nfolds=0)
	model.R.gamma.identity <- glm(formula=R.formula, data=R.data[,c(1:5,7:9)], family=Gamma(link=identity), na.action=na.omit)
	
	print("Compare model deviances for link function identity")
	deviance.h2o.identity = model.h2o.gamma.identity@model$training_metrics@metrics$residual_deviance / model.h2o.gamma.identity@model$training_metrics@metrics$null_deviance
	deviance.R.identity = deviance(model.R.gamma.identity)  / model.h2o.gamma.identity@model$training_metrics@metrics$null_deviance
	difference = deviance.R.identity - deviance.h2o.identity
	if (difference > 0.01) {
		print(cat("Deviance in H2O: ", deviance.h2o.identity))
		print(cat("Deviance in R: ", deviance.R.identity))
		checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	}


}

h2oTest.doTest("Comparison of H2O to R with varying link functions for the GAMMA family", test.linkFunctions)


