######################################################################
# Test for PUB-778
# No AIOOBE using the hepatitis dataset with SRF parameters:
# Response =C1
# Ignore all columns except :C9, C12, C13, C15, C16, C18
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

test.colapply <- function(conn) {
  Log.info('Uploading hepatitis data from jira web to H2O...')
  hep <- h2o.importFile(conn, path=locate("smalldata/hepatitis.data.txt"), "hep")
  
  Log.info('Print head of dataset')
  Log.info(head(hep))
 
  m <- h2o.randomForest(x = c("C9", "C12", "C13", "C15", "C16", "C18"), y = "C1", data = hep, ntree = 10, depth = 100, importance = T) 

  print(m)  
  testEnd()
}

doTest("PUB-169 Test: Apply scale over columns", test.colapply)
