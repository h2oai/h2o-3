setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# NOPASS TEST: The following bug is associated with JIRA PUB-874
# 'Discrepancy Reporting GLM Cross Validation Models in R'
# Testing R's glm model with cross validation on for Binomial and Gaussian distribution
##






test <- function() {

	print("Reading in Mushroom data for binomial glm.")
	mushroom.train <-  h2o.importFile(h2oTest.locate("smalldata/glm_test/Mushroom.gz"), destination_frame="mushroom.train")
	mushroom.train$label <- ifelse(mushroom.train$"C1"=="e",1,0)
	myX <- c(2:23)
	myY <- "label"
	print("Creating model with CV")
	h2o.glm.CV <- h2o.glm(x=myX, y=myY, training_frame=mushroom.train, model_id="h2o.glm.CV.mushroom", family="binomial",
						  alpha=1, lambda_search=T, nfolds=3, standardize = TRUE)
	print(h2o.glm.CV)  #Confirm reported values accurate and match browser

	print("Reading in Abalone data for gaussian glm.")
	abalone.train <-  h2o.importFile(h2oTest.locate("smalldata/glm_test/Abalone.gz"), destination_frame="abalone.train")
	myX <- c(1:8)
	myY <- "C9"
	print("Creating model with CV")
	h2o.glm.CV <- h2o.glm(x=myX, y=myY, training_frame=abalone.train, model_id="h2o.glm.CV.abalone", family="gaussian",
						  alpha=1, lambda_search=T, nfolds=3, standardize = TRUE)
	print(h2o.glm.CV)  #Confirm reported values accurate and match browser

  
}

h2oTest.doTest("Testing R's glm model with cross validation on for Binomial and Gaussian distribution", test)
