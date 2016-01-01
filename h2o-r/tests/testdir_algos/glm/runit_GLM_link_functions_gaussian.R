setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
##
# Comparison of H2O to R with varying link functions for the GAUSSIAN family on prostate dataset
# Link functions: identity (canonical link)
#				  log
#				  inverse
##





test.linkFunctions <- function() {

	print("Read in prostate data.")
	h2o.data = h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")    
#	print(head(h2o.data))
        head(h2o.data)
	R.data = as.data.frame(as.matrix(h2o.data))

        foo = h2o.data[,2:9]
        foo = R.data[,2:9]

	print("Testing for family: GAUSSIAN")
	print("Set variables for h2o.")
	myY = "GLEASON"
	myX = c("ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
	print("Define formula for R")
	R.formula = (R.data[,"GLEASON"]~.) 

	print("Create models with canonical link: IDENTITY")
	model.h2o.gaussian.identity <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="gaussian", link="identity",alpha=0.5, lambda=0, nfolds=0)
        foo = R.data[,2:9]
	model.R.gaussian.identity <- glm(formula=R.formula, data=R.data[,2:9], family=gaussian(link=identity), na.action=na.omit)
	
	print("Compare model deviances for link function identity")
	deviance.h2o.identity = model.h2o.gaussian.identity@model$training_metrics@metrics$residual_deviance / model.h2o.gaussian.identity@model$training_metrics@metrics$null_deviance
	deviance.R.identity = deviance(model.R.gaussian.identity)  / model.h2o.gaussian.identity@model$training_metrics@metrics$null_deviance
	difference = deviance.R.identity - deviance.h2o.identity
	if (difference > 0.01) {
		print(cat("Deviance in H2O: ", deviance.h2o.identity))
		print(cat("Deviance in R: ", deviance.R.identity))
		checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	}

	print("")
	print("|=======================================================|")
	print("WARNING: Additonal log functions in tests skipped over.")
	print("The following is associated with PUB-965")
	print("|=======================================================|")
	print("")
	
	#Issue with unspecified start values:

	# print("Create models with link: LOG")
	# model.h2o.gaussian.log <- h2o.glm(x=myX, y=myY, data=h2o.data, family="gaussian", link="log",alpha=0.5, lambda=0, nfolds=0)
	# model.R.gaussian.log <- glm(formula=R.formula, data=R.data[,2:9], family=gaussian(link=log), na.action=na.omit)
	
	# print("Compare model deviances for link function log")
	# deviance.h2o.log = model.h2o.gaussian.log@model$deviance / model.h2o.gaussian.log@model$null
	# deviance.R.log = deviance(model.R.gaussian.log)  / model.h2o.gaussian.log@model$null
	# difference = deviance.R.log - deviance.h2o.log
	# if (difference > 0.01) {
	# 	print(cat("Deviance in H2O: ", deviance.h2o.log))
	# 	print(cat("Deviance in R: ", deviance.R.log))
	# 	checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	# }
	

	#Issue with non positive values:

	# print("Create models with link: INVERSE")
	# model.h2o.gaussian.inverse <- h2o.glm(x=myX, y=myY, data=h2o.data, family="gaussian", link="inverse",alpha=0.5, lambda=0, nfolds=0)
	# model.R.gaussian.inverse <- glm(formula=R.formula, data=R.data[,2:9], family=gaussian(link=inverse), na.action=na.omit)
	
	# print("Compare model deviances for link function inverse")
	# deviance.h2o.inverse = model.h2o.gaussian.inverse@model$deviance / model.h2o.gaussian.inverse@model$null
	# deviance.R.inverse = deviance(model.R.gaussian.inverse)  / model.h2o.gaussian.inverse@model$null
	# difference = deviance.R.inverse - deviance.h2o.inverse
	# if (difference > 0.01) {
	# 	print(cat("Deviance in H2O: ", deviance.h2o.inverse))
	# 	print(cat("Deviance in R: ", deviance.R.inverse))
	# 	checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	# }
	

}

h2oTest.doTest("Comparison of H2O to R with varying link functions for the GAUSSIAN family", test.linkFunctions)


