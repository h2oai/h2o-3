setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_1235 <- function() {
  h2oTest.logInfo("Import Chicago crimes data...")
  crimes <- h2o.importFile(h2oTest.locate("smalldata/chicago/chicagoCrimes10k.csv.zip"))
  print(summary(crimes))
  
  h2oTest.logInfo("Split frame with ratios = c(0.8,0.19999999)")
  crimesSplit <- h2o.splitFrame(crimes, ratios = c(0.8,0.199999999))
  
}

h2oTest.doTest("PUB-1235: h2o.splitFrame causes AIOOBE", test.pub_1235)
