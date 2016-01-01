setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# Parse the header,test  and train files
# Build glm model with lambda search
# Predict using all models and calculate auc for each model
# Compare the aucs returned with those calculated by ROCR package




test.pub.822 <- function() {
print("Parse header file")
spect_header <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/SPECT_header.txt")),destination_frame = "spect_header")
print("Parse train and test files")
spect_train <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/SPECT_train.txt")),destination_frame = "spect_train",col.names=spect_header)
spect_test <- h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/SPECT_test.txt")),destination_frame = "spect_test", col.names=spect_header)

print("Summary of the train set")
print(summary(spect_train))
print(str(spect_train))

print("As all columns in the dataset are binary, converting the datatype to factors")
for(i in 1:length(colnames(spect_train))){
  spect_train[,i] <- as.factor(spect_train[,i])
  spect_test[,i] <- as.factor(spect_test[,i])
}
print(summary(spect_train))
print(summary(spect_test))

print("Build GLM model")
myX <- 2:length(colnames(spect_train))
myY <- 1
my.glm <- h2o.glm(x=myX, y=myY, training_frame=spect_train, family="binomial", standardize=T, lambda_search=T)
print(my.glm)

print("Predict models on test set and print AUC")
print("Also Check if auc from H2O is correct by checking it against ROCR's auc")

for(i in 1:100){

	pred <- predict(my.glm@models[[i]],spect_test)
	perf <- h2o.performance(pred$'1',spect_test$OVERALL_DIAGNOSIS )
	auc_h <- perf@model$auc

	predic = prediction(as.data.frame(pred$'1'),as.data.frame(spect_test$OVERALL_DIAGNOSIS))
	perfor <- performance(predic,"auc")
	auc_R = as.numeric(perfor@y.values)

	print(paste("model: ",i, "  auc from H2O: ",auc_h , "  auc from ROCR:", auc_R,sep =''))
	expect_equal(auc_h,auc_R)
}


  
}

h2oTest.doTest("Test pub 822", test.pub.822)
