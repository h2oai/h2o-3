#----------------------------------------------------------------------
# Purpose:  This test exercises HDFS operations from R.
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')


#----------------------------------------------------------------------
# Parameters for the test.
#----------------------------------------------------------------------

# Check if we are running inside the 0xdata network by seeing if we can touch
# the HDP2.1 namenode. Update if using other clusters.
# Note this should fail on home networks, since 176 is not likely to exist
# also should fail in ec2.
running_inside_hexdata = url.exists("http://mr-0xd6:50070", timeout=5)

if (running_inside_hexdata) {
    # hdp2.1 cluster
    hdfs_name_node = "mr-0xd6"    
    hdfs_airlines_file = "/datasets/airlines_all.csv"
    hdfs_airlines_dir  = "/datasets/airlines_test_train"
} else {
    stop("Not running on 0xdata internal network.  No access to HDFS.")
}

#----------------------------------------------------------------------


heading("BEGIN TEST")
conn <- h2o.init(ip=myIP, port=myPort)

#----------------------------------------------------------------------
# Single file cases.
#----------------------------------------------------------------------

heading("Testing single file importHDFS")
url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_airlines_file)
airlines.hex <- h2o.importFile(conn, url)
head(airlines.hex)
tail(airlines.hex)
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
IgnoreCols <- c('DepTime', 'CRSDepTime', 'ArrTime', 'CRSArrTime', 'ArrDelay', 'DepDelay', 'TaxiIn', 
                'TaxiOut', 'Cancelled', 'FlightNum', 'CancellationCode', 'Diverted', 'CarrierDelay', 
                'WeatherDelay', 'NASDelay', 'SecurityDelay', 'LateAircraftDelay', 'IsArrDelayed', 
                'IsDepDelayed')

## Then remove those cols from validX list
myX <- which(!(names(airlines.hex) %in% IgnoreCols))

## Chose which col as response
DepY <- "IsDepDelayed"
ArrY <- "IsDepDelayed"

# Chose functions glm, gbm, deeplearning

# obj name | function call | x = predictors | y = response | training_frame = airlines
# 
air.glm <- h2o.glm(x = myX, y = DepY, training_frame = airlines.hex, family = "binomial")
air.dl  <- h2o.deeplearning(x = myX, y = DepY, training_frame = airlines.hex, epochs=1, hidden=c(50,50))
air.gbm <- h2o.gbm(x = myX, y = DepY, training_frame = airlines.hex, loss = "bernoulli", ntrees=5)
PASS_BANNER()
