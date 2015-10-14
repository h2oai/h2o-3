library(h2o)
h2o.init()

filePath <- normalizePath(h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip"))
testFilePath <- normalizePath(h2o:::.h2o.locate("smalldata/airlines/allyears2k_headers.zip"))

# Uploading data file to h2o.
air <- h2o.importFile(path = filePath, destination_frame = "air")

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

# Add a new column to the exsiting dataset.
# This is cheap, since H2O data frames are mutable.  The data is not copied, we just add a new column.
air$DepTimeNoNAs <- ifelse(is.na(air$DepTime), DepTime_mean, air$DepTime)
numNAs <- sum(is.na(air$DepTimeNoNAs))
stopifnot(numNAs == 0)
mean(air$DepTimeNoNAs)

# Or, just replace the existing column in place.
# This is also cheap.
air$DepTime <- ifelse(is.na(air$DepTime), DepTime_mean, air$DepTime)
numNAs <- sum(is.na(air$DepTime))
stopifnot(numNAs == 0)
mean(air$DepTime)
