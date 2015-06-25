#----------------------------------------------------------------------
# Purpose:  Split Boston Housing dataset into train and test sets.
#           Build Regression models and predict on a test Set.
#           Print Mean Squared errors on test set
# Dataset location: http://archive.ics.uci.edu/ml/datasets/Housing
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')
test <- function(h) {
	#If you want to run the below code in R terminal, add the next two commented lines that inports h2o library into R and starts H2O cloud
	#Then modify file path on line 16 to specify full path to the data file, like- "/Users/.../.."
	#Copy paste the code of the function in the R terminal
	# library(h2o)
	# h <- h2o.init()

	filePath <- normalizePath(locate("smalldata/gbm_test/BostonHousing.csv"))
	print(filePath)
	#Import data into H2O
	BostonHousing <- h2o.uploadFile(h, path = filePath, destination_frame= "BostonHousing")
	print(BostonHousing)
	BostonHousing <-  h2o.importFile(h, path = filePath, destination_frame="BostonHousing")
	print(dim(BostonHousing))
	str(BostonHousing)
	#Convert column type to factor
	BostonHousing$chas <- as.factor(BostonHousing$chas)

	#Split the dataset into train and test sets
	s <- h2o.runif(BostonHousing)    # Useful when number of rows too large for R to handle
	BH_train <- h2o.assign(BostonHousing[s <= 0.8,],key="BH_train")
	print(dim(BH_train))
	BH_test <- h2o.assign(BostonHousing[s > 0.8,],key="BH_test")
	print(dim(BH_test))

	myX <- 1:13
	myY <- "medv"

	#Build gbm models by running a grid over interaction depth
	my_gbm <- h2o.gbm(x=myX,y=myY,distribution="gaussian",training_frame=BH_train,ntrees=500,
	                      max_depth=c(2,3,4),learn_rate=0.01)
	print(my_gbm)
	#my_gbm is an S4 object and mse for all trees can be accessed for say, the first model, using following syntax
	my_gbm@model[[1]]@model$err
	#if a non grid job is run then the syntax will be-
	# my_gbm@model$err


	#Build randomForest models by running a grid over ntrees
	# Note: at present fast mode(default) random forest does not support regression.Change mode to BigData, by setting type = "BigData" in the function call
	# In BigData mode stat.type is ignored and only mse is used as the split measure.
	# Check ?h2o.randomForest for more info
	my_rf <- h2o.randomForest(x=myX,y=myY,training_frame=BH_train,validation_frame=BH_train,
                         ntrees=c(100,200,300),max_depth=10)
	print(my_rf)
	#Access mse's for the first model
	my_rf@model[[1]]@model$mse


	#Prediction
	print("Summary of gbm on Boston Housing dataset, MSE reported on test set")
	for(i in 1:3){
  		model_obj <- my_gbm@model[[i]]
  		gbm_pred <- predict(object=model_obj,newdata=BH_test)
  		#if a non grid job is run, then the command will be
  		#gbm_pred = predict(my_gbm, newdata = BH_test)
  		gbm_pred
  		#Calculate the mean squared error for the test set
  		MSE <- mean(((BH_test$medv-gbm_pred)^2))

  		#Access the params of the built model
  		trees <- model_obj@model$params$n.trees
  		shrinkage <- model_obj@model$params$shrinkage
  		depth <- model_obj@model$params$interaction.depth

  		print(paste ("ntree=",trees, "  shrinkage=",shrinkage, "  interaction_depth=",
               depth, "  MSE_on_Test_set=", round(MSE,2), sep=''))
	}

	print("Summary of randomForest on Boston Housing dataset, MSE reported on test set")
	for(i in 1:3){
  		model_obj <- my_rf@model[[i]]
  		rf_pred <- predict(object=model_obj,newdata=BH_test)
  		#if a non grid job is run, then the command will be
  		#rf_pred = predict(my_rf, newdata = BH_test)
  		rf_pred
  		#Calculate the mean squared error on the test set
  		MSE  <- mean(((BH_test$medv-rf_pred)^2))

  		#Access the params of the model
  		trees <- model_obj@model$params$ntree
  		depth <- model_obj@model$params$depth

  		print(paste ("ntree=",trees, "  depth=",
               depth, "  MSE_on_Test_set=", round(MSE,2), sep=''))
	}

testEnd()
}

doTest("Regression modeling, Split data into test/train, do grid search on gbm and rf and predict on test set, print the mse's and model params ", test)
