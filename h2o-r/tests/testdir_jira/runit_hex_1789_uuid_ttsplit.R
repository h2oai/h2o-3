setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for HEX-1789
# UUID
######################################################################

# setwd("/Users/tomk/0xdata/ws/h2o/R/tests/testdir_jira")


options(echo=TRUE)


test.uuid <- function() {
  h2oTest.logInfo('Importing test_uuid.csv to H2O...')
  df <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/jira/test_uuid.csv')))
  colnames(df) <- c("AA", "UUID", "CC")
  
  h2oTest.logInfo("Slice a subset of columns 1")
  df.train <- df[df$CC == 1,]
  
  df2 <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/jira/test_uuid_na.csv')))
  colnames(df2) <- c("AA", "UUID", "CC")

  h2oTest.logInfo("Slice a subset of columns 2")
  df2.train <- df2[df2$CC == 1,]
  df2.test  <- df2[df2$CC == 0,]

  
}

h2oTest.doTest("HEX-1789 Test: UUID", test.uuid)
