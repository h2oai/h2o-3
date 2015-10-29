library(h2o)
h2o.init()

filePath <- normalizePath(h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
testFilePath <- normalizePath(h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip"))

#conn <- h2o.init(ip=myIP, port=myPort, startH2O=FALSE)

# Uploading data file to h2o.
air <- h2o.importFile(filePath, "air")
airCopy <- air[,1L:ncol(air)]  # make a copy so we can revert our imputations easily

# Print dataset size.
dim(air)

#
# Example 1.  Impute mean into a numeric column.
#

# Show the number of rows with NA.
numNAs <- sum(is.na(air$DepTime))
stopifnot(numNAs == 1086)
if (numNAs == nrow(air)) {
  stop("Can't impute if there is no data in the column at all.")
}

DepTime_mean <- mean(air$DepTime, na.rm = TRUE)
DepTime_mean

# impute the column with h2o.impute(...)
air_imputed <- h2o.impute(air, "DepTime", method = "median", combine_method="lo")
numNAs <- sum(is.na(air_imputed$DepTime))
stopifnot(numNAs == 0)

# revert imputations
air <- airCopy[,1L:ncol(airCopy)]

# impute the column using a grouping based on the Origin and Distance
# NB: If the Origin and Distance produce groupings of NAs, then no imputation will be done (NAs will result).
h2o.impute(air, "DepTime", method = "mean", by = c("Origin", "Distance"))

# revert imputations
air <- airCopy[,1L:ncol(airCopy)]

# impute a factor column by the most common factor in that column
h2o.impute(air, "TailNum", method = "mode")

# revert imputations
air <- airCopy[,1L:ncol(airCopy)]

# impute a factor column using a grouping based on the Month and Year
h2o.impute(air, "TailNum", method = "mode", by=c("Month", "Year"))