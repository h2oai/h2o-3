setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pub_1235 <- function() {
  Log.info("Import Chicago crimes data...")
  crimes <- h2o.importFile(locate("smalldata/chicago/chicagoCrimes10k.csv.zip"))
  print(summary(crimes))
  
  Log.info("Split frame with ratios = c(0.8,0.2)")
  crimesSplit <- h2o.splitFrame(crimes, ratios = c(0.8,0.2))
  testEnd()
}

doTest("PUB-1235: h2o.splitFrame causes AIOOBE", test.pub_1235)
