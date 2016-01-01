setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



#import multimodal data set; parse as FV
test.summary2.numeric <- function() {
  h2oTest.logInfo("Importing wonkysummary.csv data...")
  wonkysummary.hex <- h2o.importFile(h2oTest.locate("smalldata/jira/wonkysummary.csv"), "wonky.hex")

#check that summary2 gives expected output
  h2oTest.logInfo("Check that summary gives output...")
  summary(wonkysummary.hex)
  summary_ <- summary(wonkysummary.hex)
  h2oTest.logInfo("Check that we get a table back from the summary(hex)")
  expect_that(summary_, is_a("table"))


#check produced values against known values
  h2oTest.logInfo("Check that the summary from H2O matches known good values: ")
  H2Osum<- summary(wonkysummary.hex)
  wonky.df<- read.csv(h2oTest.locate("smalldata/jira/wonkysummary.csv"))
  wonky.Rsum<-as.table(summary(wonky.df))
  
  h2oTest.logInfo("R's summary:")
  print(summary(wonky.df))
 
  h2oTest.logInfo("H2O's Summary:")
  print(summary_)

  h2oTest.checkSummary(H2Osum, wonky.Rsum) 
  
}

h2oTest.doTest("Summary Tests", test.summary2.numeric)

