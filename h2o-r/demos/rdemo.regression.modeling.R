library(h2o)
h2o.init()

filePath <- normalizePath(h2o:::.h2o.locate("smalldata/gbm_test/BostonHousing.csv"))
print(filePath)
#Import data into H2O
BostonHousing <- h2o.uploadFile(path = filePath, destination_frame= "BostonHousing")
print(BostonHousing)
BostonHousing <-  h2o.importFile(path = filePath, destination_frame="BostonHousing")
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
grid_space1 <- list()
grid_space1$max_depth <- c(2,3,4)
grid_space1$ntrees <- c(500)
grid_space1$learn_rate <- c(0.01)
grid_space1$distribution <- c("gaussian")
my_gbm_grid <- h2o.grid("gbm",x=myX,y=myY,training_frame=BH_train,hyper_params=grid_space1)
print(my_gbm_grid)

#Build randomForest models by running a grid over ntrees
# Note: at present fast mode(default) random forest does not support regression.Change mode to BigData, by setting type = "BigData" in the function call
# In BigData mode stat.type is ignored and only mse is used as the split measure.
# Check ?h2o.randomForest for more info
grid_space2 <- list()
grid_space2$max_depth <- c(10)
grid_space2$ntrees <- c(100,200,300)
my_rf_grid <- h2o.grid("randomForest",x=myX,y=myY,training_frame=BH_train,validation_frame=BH_train,hyper_params=grid_space2)
print(my_rf_grid )

#Prediction
print("Summary of gbm on Boston Housing dataset, MSE reported on test set")
for(i in 1:3){
  		model_obj <- h2o.getModel(my_gbm_grid@model_ids[[i]])
  		gbm_pred <- predict(model_obj,BH_test)
  		#if a non grid job is run, then the command will be
  		#gbm_pred = predict(my_gbm_grid, newdata = BH_test)
  		gbm_pred
  		#Calculate the mean squared error for the test set
  		MSE <- mean(((BH_test$medv-gbm_pred)^2))

  		#Access the params of the built model
  		trees <- model_obj@model$params$ntrees
  		learn_rate <- model_obj@model$params$learn_rate
  		depth <- model_obj@model$params$max_depth

  		print(paste ("ntree=",trees, "  learn_rate=",learn_rate, "  max_depth=", depth, "  MSE_on_Test_set=", round(MSE,2), sep=''))
}

print("Summary of randomForest on Boston Housing dataset, MSE reported on test set")
for(i in 1:3){
		model_obj <- h2o.getModel(my_rf_grid@model_ids[[i]])
  		rf_pred <- predict(model_obj,BH_test)
  		#if a non grid job is run, then the command will be
  		#rf_pred = predict(my_rf_grid , newdata = BH_test)
  		rf_pred
  		#Calculate the mean squared error on the test set
  		MSE  <- mean(((BH_test$medv-rf_pred)^2))

  		#Access the params of the model
  		trees <- model_obj@model$params$ntrees
  		depth <- model_obj@model$params$max_depth

  		print(paste ("ntree=",trees, "  depth=", depth, "  MSE_on_Test_set=", round(MSE,2), sep=''))
}