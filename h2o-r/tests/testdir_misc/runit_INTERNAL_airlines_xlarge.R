setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

hdfs_name_node = HADOOP.NAMENODE
hdfs_airlines_file = "/datasets/airlines_all.csv"
#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

h2oTest.heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_airlines_file)
data.hex <- h2o.importFile(url)

n <- nrow(data.hex)
print(n)
if (n != 116695259) {
    stop("nrows is wrong")
}

if (class(data.hex) != "H2OFrame") {
    stop("data.hex is the wrong type")
}
print ("Import worked")

## First choose columns to ignore
IgnoreCols <- c('DepTime','ArrTime','FlightNum','TailNum','ActualElapsedTime','AirTime','ArrDelay','DepDelay','TaxiIn','TaxiOut','Cancelled','CancellationCode','CarrierDelay','WeatherDelay','NASDelay','SecurityDelay','LateAircraftDelay','Diverted')

## Then remove those cols from validX list
myX <- which(!(names(data.hex) %in% IgnoreCols))

## Chose which col as response
DepY <- "IsDepDelayed"

# Chose functions glm, gbm, deeplearning
# obj name | function call | x = predictors | y = response | training_frame = airlines
#

## Build GLM Model and compare AUC with h2o1
air.glm <- h2o.glm(x = myX, y = DepY, training_frame = data.hex, family = "binomial")
pred_glm = predict(air.glm, data.hex)
perf_glm <- h2o.performance(air.glm, data.hex)
auc_glm <- h2o.auc(perf_glm)
print(auc_glm)
expect_true(abs(auc_glm - 0.79) < 0.01)

IgnoreCols_1 <- c('Year','Month','DayofMonth','DepTime','DayOfWeek','ArrTime','TailNum','ActualElapsedTime','AirTime','ArrDelay','DepDelay','TaxiIn','TaxiOut','Cancelled','CancellationCode','Diverted','CarrierDelay','WeatherDelay','NASDelay','SecurityDelay','LateAircraftDelay')

## Then remove those cols from validX list
myX1 <- which(!(names(data.hex) %in% IgnoreCols_1))

air.gbm <- h2o.gbm(x = myX1, y = DepY, training_frame = data.hex, distribution = "bernoulli", ntrees=50)
pred_gbm = predict(air.gbm, data.hex)
perf_gbm <- h2o.performance(air.gbm, data.hex)
auc_gbm <- h2o.auc(perf_gbm)
print(auc_gbm)
expect_true(abs(auc_gbm - 0.80) < 0.01)

air.dl  <- h2o.deeplearning(x = myX1, y = DepY, training_frame = data.hex, epochs=1, hidden=c(50,50), loss = "CrossEntropy")
pred_dl = predict(air.dl, data.hex)
perf_dl <- h2o.performance(air.dl, data.hex)
auc_dl <- h2o.auc(perf_dl)
print(auc_dl)
expect_true(abs(auc_dl - 0.80) <= 0.02)

}

h2oTest.doTest("Test",rtest)
