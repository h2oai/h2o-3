setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



#------------------#
# Testing on HTTPS #
#------------------#

test.import.https <- function() {

  url <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"

  t <- system.time(aa <- h2o.importFile(url))
  print(aa)
  print(t)

  
}

h2oTest.doTest("Testing HTTPS File Import", test.import.https)
