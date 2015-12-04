setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.colapply <- function() {
  Log.info('Uploading hepatitis data from jira web to H2O...')
  hep <- h2o.importFile( path=locate("smalldata/drf_test/hepatitis.data.txt"), "hep")
  
  Log.info('Print head of dataset')
  Log.info(head(hep))
 
  m <- h2o.randomForest(x = c("C9", "C12", "C13", "C15", "C16", "C18"), y = "C1", training_frame = hep, ntrees = 10, max_depth = 100)

  print(m)  
}

doTest("PUB-169 Test: Apply scale over columns", test.colapply)
