


#-----------------#
# Testing on HTTP #
#-----------------#

test.import.http <- function() {
  url <- "http://s3.amazonaws.com/h2o-public-test-data/smalldata/prostate/prostate.csv.zip"

  t <- system.time(aa <- h2o.importFile( url))
  print(aa)
  print(t)

  
}

doTest("Testing HTTP File Import", test.import.http)
