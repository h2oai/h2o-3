setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')


test <- function() {
  json_file <- locate("smalldata/jira/hex-1833.json")

  print(fromJSON(file=json_file))
 
  testEnd()
}

doTest("testing JSON parse", test)
