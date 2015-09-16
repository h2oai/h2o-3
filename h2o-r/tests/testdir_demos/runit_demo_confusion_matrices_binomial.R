#----------------------------------------------------------------------
# Purpose:  Demostrate confusionMatrix usages
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

options(echo=TRUE)

heading("BEGIN TEST")

# RStudio interactive mode
if (! exists("myIP")) {
  library(h2o)
  myIP = "localhost"
  myPort = 54321
}

conn <- h2o.init(ip=myIP, port=myPort, startH2O=FALSE)

#uploading data file to h2o
air <- h2o.importFile(path=h2o:::.h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train <- air[s <= 0.8,]
air.valid <- air[s > 0.8,]

myX <- c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY <- "IsDepDelayed"

#gbm
air.gbm <- h2o.gbm(x = myX, y = myY, training_frame = air.train, validation_frame = air.valid,
                   distribution = "bernoulli", ntrees = 100, max_depth = 3, learn_rate = 0.01)

# Show various confusion matrices for training dataset (based on metric(s))
h2o.confusionMatrix(air.gbm) # maximum f1 threshold chosen by default

h2o.confusionMatrix(air.gbm, metrics="f2")

h2o.confusionMatrix(air.gbm, metrics="precision")

cms <- h2o.confusionMatrix(air.gbm, metrics=list("accuracy", "f0point5"))
cms[[1]]
cms[[2]]

# Show various confusion matrices for training dataset (based on threshold(s))
h2o.confusionMatrix(air.gbm, thresholds=0.77)

cms <- h2o.confusionMatrix(air.gbm, thresholds=list(0.1, 0.5, 0.99))
cms[[1]]
cms[[2]]
cms[[3]]

# Show various confusion matrices for validation dataset (based on metric(s))
h2o.confusionMatrix(air.gbm, metrics="f2", valid=T)

h2o.confusionMatrix(air.gbm, metrics="precision", valid=T)

cms <- h2o.confusionMatrix(air.gbm, metrics=list("accuracy", "f0point5"), valid=T)
cms[[1]]
cms[[2]]

# Show various confusion matrices for validation dataset (based on threshold(s))
h2o.confusionMatrix(air.gbm, thresholds=0.77)

cms <- h2o.confusionMatrix(air.gbm, thresholds=list(0.25, 0.33, 0.44))
cms[[1]]
cms[[2]]
cms[[3]]

# Show various confusion matrices for validation dataset (based on metric(s) AND threshold(s))
cms <- h2o.confusionMatrix(air.gbm, thresholds=0.77, metrics="f1")
cms[[1]]
cms[[2]]

cms <- h2o.confusionMatrix(air.gbm, thresholds=list(0.25, 0.33), metrics=list("f2", "f0point5"))
cms[[1]]
cms[[2]]
cms[[3]]
cms[[4]]

# Test dataset
air.test <- h2o.importFile(path=h2o:::.h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))

# Test performance
perf.gbm <- h2o.performance(air.gbm, air.test)

# Show various confusion matrices for test dataset (based on metric(s))
h2o.confusionMatrix(perf.gbm, metrics="f0point5")

h2o.confusionMatrix(perf.gbm, metrics="min_per_class_accuracy")

cms <- h2o.confusionMatrix(perf.gbm, metrics=list("accuracy", "f0point5"))
cms[[1]]
cms[[2]]

# Show various confusion matrices for test dataset (based on threshold(s))
h2o.confusionMatrix(perf.gbm, thresholds=0.5)

cms <- h2o.confusionMatrix(perf.gbm, thresholds=list(0.01, 0.75, .88))
cms[[1]]
cms[[2]]
cms[[3]]

PASS_BANNER()
