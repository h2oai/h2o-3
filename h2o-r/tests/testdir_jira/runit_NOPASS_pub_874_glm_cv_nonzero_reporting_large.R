##
# NOPASS TEST: The following bug is associated with JIRA PUB-874
# 'Discrepancy Reporting GLM Cross Validation Models in R'
# Testing R's glm model with cross validation on for Binomial and Gaussian distribution
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')


test <- function(conn) {
  
	print("Reading in Mushroom data for binomial glm.")
	mushroom.train <-  h2o.importFile(conn, locate("smalldata/Mushroom.gz"), key="mushroom.train")
	mushroom.train$label <- ifelse(mushroom.train$"C1"=="e",1,0)
	myX <- c(2:23)
	myY <- "label"
	print("Creating model with CV")
	h2o.glm.CV <- h2o.glm(x=myX, y=myY, data=mushroom.train, key="h2o.glm.CV.mushroom", family="binomial", alpha=1, higher_accuracy=T, lambda_search=T, nfolds=3,variable_importances=TRUE, use_all_factor_levels=TRUE)
	print(h2o.glm.CV)  #Confirm reported values accurate and match browser

	print("Reading in Abalone data for gaussian glm.")
	abalone.train <-  h2o.importFile(conn, locate("smalldata/Abalone.gz"), key="abalone.train")
	myX <- c(1:8)
	myY <- "C9"
	print("Creating model with CV")
	h2o.glm.CV <- h2o.glm(x=myX, y=myY, data=abalone.train, key="h2o.glm.CV.abalone", family="gaussian", alpha=1, higher_accuracy=T, lambda_search=T, nfolds=3, variable_importances=TRUE, use_all_factor_levels=TRUE)
	print(h2o.glm.CV)  #Confirm reported values accurate and match browser
  
  testEnd()
}

doTest("Testing R's glm model with cross validation on for Binomial and Gaussian distribution", test)
