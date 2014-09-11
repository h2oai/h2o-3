##
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.cbind <- function(conn) {
  Log.info('test cbind')

  hdf <- h2o.uploadFile(conn, locate('../../../smalldata/jira/pub-180.csv'))
  otherhdf <- h2o.uploadFile(conn, locate('../../../smalldata/jira/v-11.csv'))

  ##### WORKS #####
  # cbind self to self
  hdf2 <- cbind(hdf, hdf)
  expect_that( dim(hdf2), equals(c(12, 8)) )

  # cbind a sliced column to a sliced column
  xx <- hdf[,1]
  yy <- hdf[,2]
  expect_that( dim(cbind(xx,yy)), equals(c(12,2)) )

  # cbind logical expressions
  hdf_filt <- cbind(hdf[,3] <= 5, hdf[,4] >= 4)
  expect_that(dim(hdf_filt), equals(c(12, 2)))
  
  # cbind sets column names correctly
  hdf_names <- cbind(colX = xx, colY = yy)
  expect_that(colnames(hdf_names), equals(c("colX", "colY")))
  
  # cbind unequal rows fails
  expect_that(cbind(hdf, otherhdf), throws_error())
  
  ##### BROKEN #####
  # cbind a df to a slice
  # Note: Not working because hdf is VA and hdf[,1] is FV
  # expect_that( dim(cbind(hdf, hdf[,1])), equals(c(12,5)) )

  testEnd()
}

doTest("test cbind", test.cbind)

