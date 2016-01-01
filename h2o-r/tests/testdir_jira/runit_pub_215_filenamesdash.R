setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#
# dash in filename test
#






dash_filename_test <- function(){
  h2oTest.logInfo('uploading testing dataset')
  df.h <- h2o.importFile(h2oTest.locate('smalldata/jira/pub-215.csv'))

  h2oTest.logInfo('printing from h2o')
  h2oTest.logInfo( head(df.h) )

  res <- as.data.frame(h2o.table(df.h$l>0))

  
}

if(F){
  # R code that does the same as above
  df <- read.csv('/Users/earl/work/h2o/smalldata/jira/pub-215.csv')
  table(df$l > 0)
}


h2oTest.doTest('dash_filename_test', dash_filename_test)
