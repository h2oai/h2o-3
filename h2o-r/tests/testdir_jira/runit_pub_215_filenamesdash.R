#
# dash in filename test
#






dash_filename_test <- function(){
  Log.info('uploading testing dataset')
  df.h <- h2o.importFile(locate('smalldata/jira/pub-215.csv'))

  Log.info('printing from h2o')
  Log.info( head(df.h) )

  res <- as.data.frame(h2o.table(df.h$l>0))

  
}

if(F){
  # R code that does the same as above
  df <- read.csv('/Users/earl/work/h2o/smalldata/jira/pub-215.csv')
  table(df$l > 0)
}


doTest('dash_filename_test', dash_filename_test)
