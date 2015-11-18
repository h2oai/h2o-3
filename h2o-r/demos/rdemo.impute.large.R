library(h2o)
h2o.init()

filePath <- h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip")

# Uploading data file to h2o.
air <- h2o.importFile(filePath, "air")

# Print dataset size.
print("=== CRUNK ===")
print(dim(air))

#
# Example 1.  Impute mean into a numeric column.
#

# Show the number of rows with NA.
print(numNAs <- sum(is.na(air$DepTime)))
if (numNAs == nrow(air)) {
stop("Can't impute if there is no data in the column at all.")
}

DepTime_mean <- mean(air$DepTime, na.rm = TRUE)
print(DepTime_mean)

# impute the column with h2o.impute(...)
air_imputed <- h2o.impute(air, "DepTime", method = "mean")   # can also have method = "median"
print(numNAs <- sum(is.na(air_imputed$DepTime)))
stopifnot(numNAs == 0)

# revert imputations
air <- h2o.importFile(filePath, "air")

# impute the column using a grouping based on the Origin and Distance
# NB: If the Origin and Distance produce groupings of NAs, then no imputation will be done (NAs will result).
h2o.impute(air, "DepTime", method = "mean", by = c("Dest"))

# revert imputations
air <- h2o.importFile(filePath, "air")

# impute a factor column by the most common factor in that column
h2o.impute(air, "TailNum", method = "mode")

# revert imputations
air <- h2o.importFile(filePath, "air")

# impute a factor column using a grouping based on the Origin
h2o.impute(air, "TailNum", method = "mode", by=c("Month"))