#----------------------------------------------------------------------
# Imputation example.
#
# Purpose:  Demonstrate basic imputation example with H2O driven from R.
#----------------------------------------------------------------------

# Source setup code to define myIP and myPort and helper functions.
# If you are having trouble running this, just set the condition to FALSE
# and hardcode myIP and myPort.
if (TRUE) {
  # Set working directory so that the source() below works.
  setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))

  if (FALSE) {
      setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_demos")
  }

  source('../h2o-runit.R')
  options(echo=TRUE)
  filePath <- normalizePath(locate("smalldata/airlines/allyears2k_headers.zip"))
  testFilePath <- normalizePath(locate("smalldata/airlines/allyears2k_headers.zip"))
} else {
  stop("need to hardcode ip and port")
  # myIP = "127.0.0.1"
  # myPort = 54321

  library(h2o)
  PASS_BANNER <- function() { cat("\nPASS\n\n") }
  filePath <- "https://raw.github.com/0xdata/h2o/master/smalldata/airlines/allyears2k_headers.zip"
  testFilePath <-"https://raw.github.com/0xdata/h2o/master/smalldata/airlines/allyears2k_headers.zip"
}

check.demo_impute <- function(conn) {

  # Uploading data file to h2o.
  air <- h2o.importFile(conn, filePath, "air")
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

  # impute the column in place with h2o.impute(...)
  h2o.impute(air, "DepTime", method = "median", combine_method="lo")   
  numNAs <- sum(is.na(air$DepTime))
  stopifnot(numNAs == 0)

  # revert imputations
  air <- airCopy[,1L:ncol(airCopy)]

  # impute the column in place using a grouping based on the Origin and Distance
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

  testEnd()
}

doTest("Basic imputation", check.demo_impute)
