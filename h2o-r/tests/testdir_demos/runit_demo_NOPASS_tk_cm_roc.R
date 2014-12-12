#----------------------------------------------------------------------
# Tom's demonstration example.
#
# Purpose:  Split Airlines dataset into train and validation sets.
#           Build model and predict on a test Set.
#           Print Confusion matrix and performance measures for test set
#----------------------------------------------------------------------

# Source setup code to define myIP and myPort and helper functions.
# If you are having trouble running this, just set the condition to FALSE
# and hardcode myIP and myPort.
if (TRUE) {
  # Set working directory so that the source() below works.
  setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

  if (FALSE) {
      setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_demos")
  }

  source('../h2o-runit.R')
  options(echo=TRUE)
  filePath <- normalizePath(locate("smalldata/airlines/AirlinesTrain.csv.zip"))
  testFilePath <- normalizePath(locate("smalldata/airlines/AirlinesTest.csv.zip"))
} else {
  stop("need to hardcode ip and port")
  # myIP = "127.0.0.1"
  # myPort = 54321

  library(h2o)
  PASS_BANNER <- function() { cat("\nPASS\n\n") }
  filePath <- "https://raw.github.com/0xdata/h2o/master/smalldata/airlines/AirlinesTrain.csv.zip"
  testFilePath <-"https://raw.github.com/0xdata/h2o/master/smalldata/airlines/AirlinesTest.csv.zip"
}

conn <- h2o.init(ip=myIP, port=myPort, startH2O=FALSE)

#uploading data file to h2o
air <- h2o.importFile(conn, filePath, "air")


#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train <- air[s <= 0.8,]
air.valid <- air[s > 0.8,]

myX = c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY="IsDepDelayed"

#gbm
air.gbm <- h2o.gbm(x = myX, y = myY, loss = "multinomial", training_frame = air.train, ntrees = 10, 
                  max_depth = 3, learn_rate = 0.01, nbins = 100, validation_frame = air.valid, variable_importance = T)
print(air.gbm@model)
air.gbm@model$auc

#RF
air.rf <- h2o.randomForest(x=myX,y=myY,data=air.train,ntree=10,depth=20,seed=12,importance=T,validation=air.valid, type = "BigData")
print(air.rf@model)

#uploading test file to h2o
air.test <- h2o.importFile(conn,testFilePath,key="air.test")

model_object <- air.rf #air.glm air.rf air.dl

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

PASS_BANNER()

if (FALSE) {
    h <- h2o.init(ip="mr-0xb1", port=60024)
    df <-h2o.importFile(h, "/home/tomk/airlines_all.csv")
    nrow(df)
    ncol(df)
    head(df)
    myX <- c("Origin", "Dest", "Distance", "UniqueCarrier", "Month", "DayofMonth", "DayOfWeek")
    myY <- "IsDepDelayed"
    air.glm <- h2o.glm(x = myX, y = myY, training_frame = df, family = "binomial", nfolds = 10, alpha = 0.25, lambda = 0.001)
    air.glm@model$confusion
}
