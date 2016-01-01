setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# NOPASS TEST: The following bug is associated with JIRA PUB-837
# 'GLM with Cross Validation: ArrayIndexOutOfBoundsException: 89'
# Testing glm cross validation performance with adult dataset
##





test <- function() {
  print("Reading in original adult data.")
  adult.train <-  h2o.importFile(h2oTest.locate("smalldata/glm_test/adult.gz"), destination_frame="adult.train")

  print("Make labels 1/0 for binomial glm")
  adult.train$label <- ifelse(adult.train$"C15"==">50K",1,0)

  print("Head of adult data: ")
  head(adult.train)
  print("Dimensions of adult data: ")
  dim(adult.train)

  print("Set variables to build models")
  myX <- c(1:14)
  myY <- "label"

  print("Creating model without CV")
  system.time(h2o.glm.model <- h2o.glm(x=myX, y=myY, training_frame=adult.train, model_id="h2o.glm.adult", family="binomial",
                                       alpha=1, lambda_search=T, nfolds=0, standardize = TRUE))
  h2o.glm.model

  print("Creating model with CV")
  system.time(h2o.glm.CV <- h2o.glm(x=myX, y=myY, training_frame=adult.train, model_id="h2o.glm.CV.adult", family="binomial",
                                       alpha=1, lambda_search=T, nfolds=5, standardize = TRUE))    # This line is failing
  h2o.glm.CV

  
}

h2oTest.doTest("Testing glm cross validation performance with adult dataset", test)
