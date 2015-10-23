##
# Comparison of H2O to R with varying link functions for the BINOMIAL family on prostate dataset
# Link functions: logit (canonical link)
#				  log
##





test.linkFunctions <- function() {

	print("Read in prostate data.")
	h2o.data <- h2o.uploadFile(locate("smalldata/prostate/prostate_complete.csv.zip"), destination_frame="h2o.data")
	R.data <- as.data.frame(as.matrix(h2o.data))

	print("Testing for family: BINOMIAL")
	print("Set variables for h2o.")
	myY <- "CAPSULE"
	myX <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
	print("Define formula for R")
	R.formula <- (R.data[,"CAPSULE"]~.)

	print("Create models with canonical link: LOGIT")
	model.h2o.binomial.logit <- h2o.glm(x=myX, y=myY, training_frame=h2o.data, family="binomial", link="logit",alpha=0.5, lambda=0, nfolds=0)
	model.R.binomial.logit <- glm(formula=R.formula, data=R.data[,4:10], family=binomial(link=logit), na.action=na.omit)

	print("Compare model deviances for link function logit")
	print(model.h2o.binomial.logit)
	res_dev = model.h2o.binomial.logit@model$training_metrics@metrics$residual_deviance
	print(res_dev)
	null_dev = model.h2o.binomial.logit@model$training_metrics@metrics$null_deviance
	print(null_dev)
	deviance.h2o.logit <-  res_dev / null_dev
	deviance.R.logit <- deviance(model.R.binomial.logit)  / model.h2o.binomial.logit@model$training_metrics@metrics$null_deviance
	difference <- deviance.R.logit - deviance.h2o.logit
	if (difference > 0.01) {
		print(cat("Deviance in H2O: ", deviance.h2o.logit))
		print(cat("Deviance in R: ", deviance.R.logit))
		checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	}

	print("")
	print("|=======================================================|")
	print("WARNING: Additonal log functions in tests skipped over")
	print("The following is associated with PUB-965")
	print("|=======================================================|")
	print("")

	#Issue with unspecified start values:

	# print("Create models with link: LOG")
	# model.h2o.binomial.log <- h2o.glm(x=myX, y=myY, data=h2o.data, family="binomial", link="log",alpha=0.5, lambda=0, nfolds=0)
	# model.R.binomial.log <- glm(formula=R.formula, data=R.data[,4:10], family=binomial(link=log), na.action=na.omit)

	# print("Compare model deviances for link function log")
	# deviance.h2o.log = model.h2o.binomial.log@model$deviance / model.h2o.binomial.log@model$null
	# deviance.R.log = deviance(model.R.binomial.log)  / model.h2o.binomial.log@model$null
	# difference = deviance.R.log - deviance.h2o.log
	# if (difference > 0.01) {
	# 	print(cat("Deviance in H2O: ", deviance.h2o.log))
	# 	print(cat("Deviance in R: ", deviance.R.log))
	# 	checkTrue(difference <= 0.01, "h2o's model's residualDeviance/nullDeviance is more than 0.01 lower than R's model's")
	# }


}

doTest("Comparison of H2O to R with varying link functions for the BINOMIAL family", test.linkFunctions)


