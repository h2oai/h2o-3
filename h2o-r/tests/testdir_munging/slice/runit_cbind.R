setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.h2o.cbind <- function() {
  Log.info('test h2o.cbind')

  hdf <- h2o.importFile(locate('smalldata/jira/pub-180.csv'))
  otherhdf <- h2o.importFile(locate('smalldata/jira/v-11.csv'))

  ##### WORKS #####
  # h2o.cbind self to self
  hdf2 <- h2o.cbind(hdf, hdf)
  expect_that( dim(hdf2), equals(c(12, 8)) )

  # h2o.cbind a sliced column to a sliced column
  xx <- hdf[,1]
  yy <- hdf[,2]
  expect_that( dim(h2o.cbind(xx,yy)), equals(c(12,2)) )

  # h2o.cbind logical expressions
  hdf_filt <- h2o.cbind(hdf[,3] <= 5, hdf[,4] >= 4)
  expect_that(dim(hdf_filt), equals(c(12, 2)))
  
  # h2o.cbind sets column names correctly
  hdf_names <- h2o.cbind(colX = xx, colY = yy)

  print(hdf_names)

  # ignore column names for now, need to impl HACK_SETCOLNAMES2
  #expect_that(colnames(hdf_names), equals(c("colX", "colY")))
  
  # h2o.cbind unequal rows fails
  expect_that(head(h2o.cbind(hdf, otherhdf)), throws_error())
  
  # h2o.cbind a df to a slice
  expect_that( dim(h2o.cbind(hdf, hdf[,1])), equals(c(12,5)) )

  
}

doTest("test h2o.cbind", test.h2o.cbind)

