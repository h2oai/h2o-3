#
# na comparisons
#






na_comparisons <- function(){
  Log.info('uploading testing dataset')
  df.h <- h2o.importFile(locate('smalldata/jira/pub_213.csv'))

  Log.info('printing from h2o')
  Log.info( head(df.h) )

  df.h[, ncol(df.h)+1] <- df.h[,1] > 0
  res <- as.data.frame(h2o.table(df.h$l>0))

  loc <- as.data.frame(df.h)


  Log.info('testing table')
  print(loc)

  print(c(1,1,1,NA,1,NA,1))
  print(is.na(loc[,3]))
  
  #expect_true(all(is.na(loc[,3]) == c(FALSE,FALSE,FALSE,TRUE,FALSE,TRUE,FALSE)))
  
}

if(F){
  # R code that does the same as above
  df <- read.csv('/Users/earl/work/h2o/smalldata/jira/pub_213.csv')
  df[,3] <- df[,1] > 0
  table(df$l > 0)
}


doTest('na_comparisons', na_comparisons)
