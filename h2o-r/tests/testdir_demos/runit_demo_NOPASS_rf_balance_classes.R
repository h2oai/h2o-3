#----------------------------------------------------------------------
# Purpose:  Split Airlines dataset into train and validation sets.
#           Build model and predict on a test Set.
#           Print Confusion matrix and performance measures for test set
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_demos")

source('../h2o-runit.R')
options(echo=TRUE)

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#uploading data file to h2o
filePath <- locate("smalldata/airlines/AirlinesTrain.csv.zip")
air <- h2o.uploadFile(conn, filePath, "air")


#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train <- air[s <= 0.8,]
air.valid <- air[s > 0.8,]

myX <- c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY <- "IsDepDelayed"

# DRF2
air.rf         <- h2o.randomForest(x = myX, y = myY, data = air.train, seed = 12, validation=air.valid, importance = T,ntree = 10, depth = 20, balance.classes=F, type = "BigData")
print(air.rf@model)

air.rf.balance <- h2o.randomForest(x = myX, y = myY, data = air.train, seed = 12, validation=air.valid,ntree = 10, depth = 20, balance.classes=T, type = "BigData")
print(air.rf.balance@model)

# randomForest
air.speedrf         <- h2o.randomForest(x = myX, y = myY, data = air.train, seed = 12, validation = air.valid, ntree = 10, depth = 20, type = "fast")
print(air.speedrf@model)

# randomForest
air.speedrf.balance <- h2o.randomForest(x = myX, y = myY, data = air.train, seed = 12, validation = air.valid,ntree = 10, depth = 20, balance.classes=T, type = "fast")
print(air.speedrf.balance@model)

#uploading test file to h2o
testFilePath <-locate("smalldata/airlines/AirlinesTest.csv.zip")
air.test <- h2o.uploadFile(conn,testFilePath,key="air.test")

func <- function(model_object) {
    #predicting on test file 
    pred <- predict(model_object,air.test)
    head(pred)
    
    #Building confusion matrix for test set
    CM <- h2o.confusionMatrix(pred$predict,air.test$IsDepDelayed)
    print(CM)
    
    #Plot ROC for test set
    perf <- h2o.performance(pred$YES,air.test$IsDepDelayed )
    print(perf)
    perf@model$precision
    perf@model$accuracy
    perf@model$auc
    plot(perf,type="roc")
}

cat("\n\nWITHOUT CLASS BALANCING\n")
func(air.rf)

cat("\n\nWITH CLASS BALANCING\n")
func(air.rf.balance)

cat("\n\nSPEEDRF WITHOUT CLASS WT\n")
func(air.speedrf)

cat("\n\nSPEEDRF WITH CLASS WT\n")
func(air.speedrf.balance)

PASS_BANNER()
