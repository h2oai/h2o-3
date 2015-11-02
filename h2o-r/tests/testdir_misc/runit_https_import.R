


#------------------#
# Testing on HTTPS #
#------------------#

test.import.https <- function() {

  url <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"

  t <- system.time(aa <- h2o.importFile(url))
  print(aa)
  print(t)

  
}

doTest("Testing HTTPS File Import", test.import.https)
