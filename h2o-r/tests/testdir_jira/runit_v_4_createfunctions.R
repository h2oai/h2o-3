#
# test uploading functions
#


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')



functiontest <- function(){
  Log.info('uploading function testing dataset')
  df.h <- h2o.importFile(locate('smalldata/jira/v-3.csv'))

  Log.info('printing from h2o')
  Log.info( head(df.h) )

  Log.info('applying over 1, 2, 1:2')
  fn1 <- function(x){ sum(x) }
  # h2o.addFunction(fn1)
  fn2 <- function(x){ x + 1 }
  # h2o.addFunction(fn2)

  df.h.1 <- apply(df.h, 1, fn1)
  df.h.2 <- apply(df.h, 2, fn1)
  # df.h.3 <- apply(df.h, 1:2, fn2)

  Log.info('pulling data locally')
  df.1 <- as.data.frame( df.h.1 )
  df.2 <- as.data.frame( df.h.2 )
  # df.3 <- as.data.frame( df.h.3 )
  print(df.1)
  print(df.2)

  expect_true(all(df.1 = c(3,7,11)))
  expect_true(all(df.2 = c(9,  12)))
  # expect_that(all( df.3[,1] == c(2,4,6) ))
  # expect_that(all( df.3[,2] == c(3,5,7) ))

  
}



doTest('function', functiontest)
