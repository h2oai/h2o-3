setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for HEX-1794
# UUID
# Issue: is.na on a UUID column was not giving correct results
######################################################################


options(echo=TRUE)


test.uuid <- function() {
  h2oTest.logInfo('Importing test_uuid.csv to H2O...')
  df <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/jira/test_uuid_na.csv')))
  colnames(df) = c("AA", "UUID", "CC")
  
  h2oTest.logInfo("Slice a subset of columns 1")
  df.nona <- df[!is.na(df$UUID),]  # this is the line appearing in the jira


  h2oTest.logInfo("How many rows dows the filter take out??")
    
  h2oTest.logInfo("Before dim(df)")
  print(dim(df))
  
  h2oTest.logInfo("After !is.na")
  print(dim(df.nona))

  expect_that(dim(df.nona)[1], is_less_than(dim(df)[1]))

  
}

h2oTest.doTest("HEX-1789 Test: UUID", test.uuid)
