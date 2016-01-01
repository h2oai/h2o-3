setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pub_174_cut <- function() {
  h2oTest.logInfo('Uploading allyears2k_header.csv to H2O...')
  air.hex <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/airlines/allyears2k_headers.zip')))
  
  h2oTest.logInfo("Cut ArrDelay column with user-specified breaks")
  air.cut <- cut(air.hex$ArrDelay, breaks = c(-1000, -20, 0, 5, 15, 60, 120, 160, 1500))
  expect_equal(nrow(air.cut), nrow(air.hex))
  
  h2oTest.logInfo("Save results to new column in dataset and run h2o.table on it")
  air.hex[,ncol(air.hex)+1] <- air.cut
  # air.table <- h2o.table(air.hex$C32, return.in.R = TRUE)
  air.table <- h2o.table(air.hex$C32)
  print(air.table)
  # expect_true(all(air.table == c(1326, 18211, 5797, 302, 8048, 256, 7245, 1598)))
  
  
}

h2oTest.doTest("PUB-174 h2o.cut on airlines data with NAs", test.pub_174_cut)

