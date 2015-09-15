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

check.demo_imputation <- function() {

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


  testEnd()
}

doTest("Basic imputation", check.demo_imputation)
