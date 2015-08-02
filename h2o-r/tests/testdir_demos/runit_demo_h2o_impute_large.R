#----------------------------------------------------------------------
# Imputation example.
#
# Purpose:  Demonstrate basic imputation example with H2O driven from R.
#----------------------------------------------------------------------

# make a copy of a dataframe
cp <- function(this) h2o.exec(this[seq(1, nrow(this), 1),seq(1, ncol(this), 1)])

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

check.demo_impute <- function(conn) {

  # Uploading data file to h2o.
  air <- h2o.importFile(conn, filePath, "air")

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
  air <- h2o.importFile(conn, filePath, "air")

  # impute the column in place using a grouping based on the Origin and Distance
  # NB: If the Origin and Distance produce groupings of NAs, then no imputation will be done (NAs will result).
  h2o.impute(air, "DepTime", method = "mean", by = c("Dest"))

  # revert imputations
  air <- h2o.importFile(conn, filePath, "air")

  # impute a factor column by the most common factor in that column
  h2o.impute(air, "TailNum", method = "mode")

  # revert imputations
  air <- h2o.importFile(conn, filePath, "air")

  # impute a factor column using a grouping based on the Origin
  h2o.impute(air, "TailNum", method = "mode", by=c("Month"))

  testEnd()
}

doTest("Basic imputation", check.demo_impute)
