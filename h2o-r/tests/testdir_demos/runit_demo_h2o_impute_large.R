setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

  if (FALSE) {
      setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_demos")
  }

  source('../h2o-runit.R')
  options(echo=TRUE)
  filePath <- locate("smalldata/airlines/allyears2k_headers.zip")
#  testFilePath <- normalizePath(locate("smalldata/airlines/allyears2k_headers.zip"))
} else {
  stop("need to hardcode ip and port")
  # myIP = "127.0.0.1"
  # myPort = 54321

  library(h2o)
  PASS_BANNER <- function() { cat("\nPASS\n\n") }

#  testFilePath <-"https://raw.github.com/0xdata/h2o/master/smalldata/airlines/allyears2k_headers.zip"
}


# Uploading data file to h2o.
air <- h2o.importFile( filePath, "air")

# Print dataset size.
dim(air)

#
# Example 1.  Impute mean into a numeric column.
#

# Show the number of rows with NA.
numNAs <- sum(is.na(air$DepTime))
if (numNAs == nrow(air)) {
  stop("Can't impute if there is no data in the column at all.")
}

DepTime_mean <- mean(air$DepTime, na.rm = TRUE)
DepTime_mean

# impute the column in place with h2o.impute(...)
h2o.impute(air, "DepTime", method = "mean")   # can also have method = "median"
numNAs <- sum(is.na(air$DepTime))
stopifnot(numNAs == 0)

# revert imputations
air <- h2o.importFile( filePath, "air")

# impute the column in place using a grouping based on the Origin and Distance
# NB: If the Origin and Distance produce groupings of NAs, then no imputation will be done (NAs will result).
h2o.impute(air, "DepTime", method = "mean", by = c("Dest"))

# revert imputations
air <- h2o.importFile( filePath, "air")

# impute a factor column by the most common factor in that column
h2o.impute(air, "TailNum", method = "mode")

# revert imputations
air <- h2o.importFile( filePath, "air")

# impute a factor column using a grouping based on the Origin
h2o.impute(air, "TailNum", method = "mode", by=c("Month"))

PASS_BANNER()
