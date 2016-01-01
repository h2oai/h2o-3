setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# NOPASS TEST: The following bug is associated with JIRA PUB-965
# 'Determine 'correct' behavior for link functions'
# Testing GLM on prostate dataset with BINOMIAL family and log link
##





test.linkFunctions <- function() {

	print("Read in prostate data.")
	prostate.data = h2o.importFile(h2oTest.locate("smalldata/prostate/prostate.csv.zip"), destination_frame="prostate.data")

	print("Run test/train split at 20/80.")
	prostate.data$split <- ifelse(h2o.runif(prostate.data)>0.8, yes=1, no=0)
	prostate.train <- h2o.assign(prostate.data[prostate.data$split == 0, c(2:10)], key="prostate.train")
	prostate.test <- h2o.assign(prostate.data[prostate.data$split == 1, c(2:10)], key="prostate.test")

	print("Testing for family: BINOMIAL")
	print("Set variables for h2o.")
	myY = "CAPSULE"
	myX = c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")

	print("Create model with link: LOG")
	model.h2o.binomial.log <- h2o.glm(x=myX, y=myY, training_frame=prostate.train, family="binomial", link="logit",alpha=0.5, lambda=0, nfolds=0)

	print("Predict")
	prediction.h2o.binomial.log <- predict(model.h2o.binomial.log, prostate.test)
	print(head(prediction.h2o.binomial.log))

	print("Check strength of predictions all within [0,1] domain")
	zero <- prediction.h2o.binomial.log[prediction.h2o.binomial.log$"p0"<0,]
	one <- prediction.h2o.binomial.log[prediction.h2o.binomial.log$"p0">1,]
	expect_equal(nrow(zero)+nrow(one), 0) # There should be no predictions with strength less than 0 or greater than 1


}

h2oTest.doTest("Testing GLM on prostate dataset with BINOMIAL family and log link", test.linkFunctions)


