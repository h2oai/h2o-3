setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#----------------------------------------------------------------------
# Purpose:  This test exercises sending lots of characters to the POST interface.
#           logAndEcho logs the message in the H2O log file, and echoes it back
#           as part of the response.
#----------------------------------------------------------------------

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_http_daemon")



check.log_and_echo <- function() {

  oneHundredChars <- "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890"
  oneThousandChars <- paste(oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, oneHundredChars, sep="")
  tenThousandChars <- paste(oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, oneThousandChars, sep="")
  twentyThousandChars <- paste(tenThousandChars, tenThousandChars, sep="")
  hundredThousandChars <- paste(tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, tenThousandChars, sep="")
  millionChars <- paste(hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, hundredThousandChars, sep="")

  echoed_message <- h2o.logAndEcho(oneHundredChars)
  n = nchar(echoed_message)
  print(n)
  if (n != 100) {
      stop("Not 100")
  }

  echoed_message <- h2o.logAndEcho(oneThousandChars)
  n = nchar(echoed_message)
  print(n)
  if (n != 1000) {
      stop("Not 1000")
  }

  echoed_message <- h2o.logAndEcho(tenThousandChars)
  n = nchar(echoed_message)
  print(n)
  if (n != 10000) {
      stop("Not 10000")
  }

  
}

h2oTest.doTest("POST log and echo tests", check.log_and_echo)
