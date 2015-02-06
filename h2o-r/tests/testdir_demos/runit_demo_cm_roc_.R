#----------------------------------------------------------------------
# Purpose:  Split Airlines dataset into train and validation sets.
#           Build model and predict on a test Set.
#           Print Confusion matrix and performance measures for test set
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

options(echo=TRUE)

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#uploading data file to h2o
air <- h2o.importFile(conn, path=locate("smalldata/airlines/AirlinesTrain.csv.zip"))

#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train <- air[s <= 0.8,]
air.valid <- air[s > 0.8,]

myX <- c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY <- "IsDepDelayed"

#gbm
air.gbm <- h2o.gbm(x = myX, y = myY, loss = "bernoulli", training_frame = air.train, ntrees = 10, max_depth = 3, learn_rate = 0.01, nbins = 100, validation_frame = air.valid)
print(air.gbm@model)

#uploading test file to h2o
air.test <- h2o.importFile(conn, path=locate("smalldata/airlines/AirlinesTest.csv.zip"))

#predicting & performance on test file
pred <- predict(air.gbm, air.test)
head(pred)
perf <- h2o.performance(air.gbm, air.test)
print(perf)

#Building confusion matrix for test set
CM <- h2o.confusionMatrices(perf, 0.5)
print(CM)

#Plot ROC for test set
h2o.precision(perf)
h2o.accuracy(perf)
h2o.auc(perf)
plot(perf,type="roc")

PASS_BANNER()


