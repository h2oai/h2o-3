setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')


test <- function(conn) {
  json_file <- locate("smalldata/jira/hex-1833.json")

  print(jsonlite::fromJSON(json_file))
 
  testEnd()
}

doTest("testing JSON parse", test)
