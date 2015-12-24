setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

if (TRUE) {
  if (FALSE) {
      setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_demos")
  }

#  source('../h2o-runit.R')
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


# Uploading data file to h2o.
air <- h2o.importFile( filePath, "air")

# Print dataset size.
dim(air)

#
# Example 1: Select all flights not departing from SFO
#

not.sfo <- air[air$Origin != "SFO",]
print(dim(not.sfo))

PASS_BANNER()
