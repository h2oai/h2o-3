#----------------------------------------------------------------------
# Purpose:  Split Airlines dataset into train and validation sets.
#           Build model and predict on a test Set.
#           Print Confusion matrix and performance measures for test set
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../findNSourceUtils.R')
options(echo=TRUE)

heading("BEGIN TEST")
conn <- new("H2OClient", ip=myIP, port=myPort)

#uploading data file to h2o
filePath <- "../../../smalldata/airlines/AirlinesTrain.csv.zip"
air = h2o.uploadFile(conn, filePath, "air")


#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s = h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train = air[s <= 0.8,]
air.valid = air[s > 0.8,]

myX = c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY="IsDepDelayed"

#gbm
air.gbm = h2o.gbm(x = myX, y = myY, distribution = "multinomial", data = air.train, n.trees = 10, 
                  interaction.depth = 3, shrinkage = 0.01, n.bins = 100, validation = air.valid, importance = T)
print(air.gbm@model)
air.gbm@model$auc

#RF
air.rf=h2o.randomForest(x=myX,y=myY,data=air.train,ntree=10,depth=20,seed=12,importance=T,validation=air.valid, type = "BigData")
print(air.rf@model)

#uploading test file to h2o
testFilePath <-"../../../smalldata/airlines/AirlinesTest.csv.zip"
air.test=h2o.uploadFile(conn,testFilePath,key="air.test")

model_object=air.rf #air.glm air.rf air.dl

#predicting on test file 
pred = h2o.predict(model_object,air.test)
head(pred)

#Building confusion matrix for test set
CM=h2o.confusionMatrix(pred$predict,air.test$IsDepDelayed)
print(CM)

#Plot ROC for test set
perf = h2o.performance(pred$YES,air.test$IsDepDelayed )
print(perf)
perf@model$precision
perf@model$accuracy
perf@model$auc
plot(perf,type="roc")

PASS_BANNER()


