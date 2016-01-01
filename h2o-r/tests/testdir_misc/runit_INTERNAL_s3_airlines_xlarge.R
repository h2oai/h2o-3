setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



rtest <- function() {

s3_airlines_file <- "h2o-airlines-unpacked/allyears2k.csv"
hdfs_airlines_file <- "/datasets/allyears2k_headers.zip"
s3_url <- paste0("s3n://", aws_id, ":", aws_key, "@", s3_airlines_file)
hdfs_url <- sprintf("hdfs://%s%s", hdfs_name_node, hdfs_airlines_file)

airlines.hex <- h2o.importFile(s3_url, header = T)
airlines_hdfs.hex <- h2o.importFile(hdfs_url, header = T)
print(summary(airlines.hex))

# Set predictor and response variables
myY <- "IsDepDelayed"
myX <- c("Origin", "Dest", "Year", "UniqueCarrier", "DayOfWeek",
         "Month", "Distance", "FlightNum")

## Simple GLM - Predict Delays
data.glm <- h2o.glm(y = myY, x = myX, training_frame = airlines.hex,
                    family = "binomial", standardize=T, lambda_search = T)

## Simple GBM
data.gbm <- h2o.gbm(y = myY, x = myX, balance_classes = T,
                    training_frame = airlines.hex, ntrees = 20, max_depth = 5,
                    distribution = "bernoulli", learn_rate = .1, min_rows = 2)

if(nrow(airlines.hex)!=nrow(airlines_hdfs.hex)) stop("# rows are not equal!")
if(ncol(airlines.hex)!=ncol(airlines_hdfs.hex)) stop("# columns not equal!")
}

h2oTest.doTest("Test",rtest)
