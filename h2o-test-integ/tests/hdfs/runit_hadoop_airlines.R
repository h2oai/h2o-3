#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit-hadoop.R')

ipPort <- get_args(commandArgs(trailingOnly = TRUE))
myIP   <- ipPort[[1]]
myPort <- ipPort[[2]]

library(RCurl)
library(h2o)

heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort, startH2O = FALSE)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS")
airlines.hex <- h2o.importFile(conn, "hdfs://datasets/airlines/airlines_all.csv")

n <- nrow(airlines.hex)
print(n)
if (n != 116695259) {
    stop("nrows is wrong")
}

if (class(airlines.hex) != "H2OFrame") {
    stop("airlines.hex is the wrong type")
}
print ("Import worked")

## First choose columns to ignore
IgnoreCols <- c('DepTime', 'CRSDepTime', 'ArrTime', 'CRSArrTime', 'ArrDelay', 'DepDelay', 'TaxiIn', 'TailNum', 'TaxiOut', 'Cancelled', 'FlightNum', 'CancellationCode', 'Diverted', 'CarrierDelay', 'WeatherDelay', 'NASDelay', 'SecurityDelay', 'LateAircraftDelay', 'IsArrDelayed', 'IsDepDelayed')

## Then remove those cols from validX list
myX <- which(!(names(airlines.hex) %in% IgnoreCols))

## Chose which col as response
DepY <- "IsDepDelayed"
ArrY <- "IsDepDelayed"

# Chose functions glm, gbm, deeplearning

# obj name | function call | x = predictors | y = response | training_frame = airlines
#
air.glm <- h2o.glm(x = myX, y = DepY, training_frame = airlines.hex, family = "binomial")
air.dl  <- h2o.deeplearning(x = myX, y = DepY, training_frame = airlines.hex, epochs=1, hidden=c(50,50), loss = "CrossEntropy")
air.gbm <- h2o.gbm(x = myX, y = DepY, training_frame = airlines.hex, loss = "bernoulli", ntrees=5)

PASS_BANNER()
