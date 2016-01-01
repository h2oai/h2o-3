setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")




test <- function() {
  json_file <- h2oTest.locate("smalldata/jira/hex-1833.json")

  print(jsonlite::fromJSON(json_file))
 
  
}

h2oTest.doTest("testing JSON parse", test)
