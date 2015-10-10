setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.h2o.rf.balance.classes <- function() {

    #uploading data file to h2o
    filePath <- locate("smalldata/airlines/AirlinesTrain.csv.zip")
    air <- h2o.uploadFile(filePath, "air")


    #Constructing validation and train sets by sampling (20/80)
    #creating a column as tall as airlines(nrow(air))
    s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
    air.train <- air[s <= 0.8,]
    air.valid <- air[s > 0.8,]

    myX <- c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
    myY <- "IsDepDelayed"

    # DRF2
    air.rf         <- h2o.randomForest(x = myX, y = myY, training_frame = air.train, seed = 12, validation_frame=air.valid, ntrees = 10, max_depth = 20, balance_classes=F)
    print(air.rf)

    air.rf.balance <- h2o.randomForest(x = myX, y = myY, training_frame = air.train, seed = 12, validation_frame=air.valid, ntrees = 10, max_depth = 20, balance_classes=T)
    print(air.rf.balance)

    #uploading test file to h2o
    testFilePath <-locate("smalldata/airlines/AirlinesTest.csv.zip")
    air.test <- h2o.uploadFile(testFilePath,destination_frame="air.test")

    func <- function(model_object) {
        #predicting on test file
        pred <- predict(model_object,air.test)
        head(pred)

        #Building confusion matrix for test set
        CM <- h2o.table(pred$predict,air.test$IsDepDelayed)
        print(CM)

        #Plot ROC for test set
        perf <- h2o.performance(model_object,air.test)
        print(perf)
        h2o.precision(perf)
        h2o.accuracy(perf)
        h2o.auc(perf)
        plot(perf,type="roc")
    }

    cat("\n\nWITHOUT CLASS BALANCING\n")
    func(air.rf)

    cat("\n\nWITH CLASS BALANCING\n")
    func(air.rf.balance)

  
}

doTest("Demo random forest balance classes feature", test.h2o.rf.balance.classes)
