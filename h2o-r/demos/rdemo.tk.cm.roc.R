library(h2o)
h2o.init()

filePath <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTest.csv.zip"
testFilePath <-"https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/AirlinesTrain.csv.zip"

h2o.startLogging()

#uploading data file to h2o
air <- h2o.importFile(filePath, "air")


#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train <- air[s <= 0.8,]
air.valid <- air[s > 0.8,]

myX = c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY="IsDepDelayed"

#gbm
air.gbm <- h2o.gbm(x = myX, y = myY, distribution = "multinomial", training_frame = air.train, ntrees = 10,
                 max_depth = 3, learn_rate = 0.01, nbins = 100, validation_frame = air.valid)

print(air.gbm)
print("Variable Importance")
print(air.gbm@model$variable_importances)

print("AUC: ")
p <- h2o.performance(air.gbm, air.valid)
print(p@metrics$AUC)

#RF
# air.rf <- h2o.randomForest(x=myX,y=myY,data=air.train,ntree=10,depth=20,seed=12,importance=T,validation=air.valid, type = "BigData")
# print(air.rf)

#uploading test file to h2o
air.test <- h2o.importFile(testFilePath,destination_frame="air.test")

model_object <- air.gbm # air.rf #air.glm air.gbm air.dl

#predicting on test file
pred <- predict(model_object,air.test)
head(pred)

perf <- h2o.performance(model_object,air.test)
#Building confusion matrix for test set

# FIXME - these require work
h2o.confusionMatrix(perf)
h2o.auc(perf)
h2o.precision(perf)
h2o.accuracy(perf)

#perf@metrics$AUC

#Plot ROC for test set
plot(perf,type="roc")
