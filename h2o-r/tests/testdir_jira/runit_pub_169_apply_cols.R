setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for PUB-169
# apply should work across columns
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")


options(echo=TRUE)


test.colapply <- function() {
  h2oTest.logInfo('Uploading cebbinom.csv to H2O...')
  cebH2O <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/jira/cebbinom.csv')))

  h2oTest.logInfo('Print head of dataset')
  h2oTest.logInfo(head(cebH2O))

  h2oTest.logInfo("Slice a subset of columns")
  cebstdH2O = cebH2O[,8:23]

  h2oTest.logInfo("Slice again and apply scale over columns")
  scaledH2O = apply(cebstdH2O[,2:16], 2, scale)
  h2oTest.logInfo(head(scaledH2O))

  cebR <- read.csv(h2oTest.locate("smalldata/jira/cebbinom.csv"))
  cebstdR <- cebR[,8:23]
  scaledR <- apply(cebstdR[,2:16], 2, scale)

  h2oTest.logInfo("Comparing results to R")
  scaledH2O.df = as.data.frame(scaledH2O)
  expect_equal(scaledH2O.df, (as.data.frame(scaledR)))

  
}

h2oTest.doTest("PUB-169 Test: Apply scale over columns", test.colapply)
