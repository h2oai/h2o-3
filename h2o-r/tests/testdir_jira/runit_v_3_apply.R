setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
#
# apply
#




applytest <- function(){
  h2oTest.logInfo('uploading apply testing dataset')
  df.h <- h2o.importFile(h2oTest.locate('smalldata/jira/v-3.csv'), "v3.hex")

  h2oTest.logInfo('printing from h2o')
  h2oTest.logInfo( head(df.h) )

  h2oTest.logInfo('applying over 1, 2, 1:2')
  df.h.1 <- apply(df.h, 1, function(x){ sum(x) })
  df.h.2 <- apply(df.h, 2, function(x){ sum(x) })
# While the semantics of apply(,1:2,) are easy (same as map), the syntactic
# form is annoying to deal with right now.  Dropped, as the alternative
# forms to get the same job done are easy & supported: df <- df+1
#  df.h.3 <- apply(df.h, 1:2, function(x){ x + 1})

  h2oTest.logInfo('pulling data locally')
  df.1 <- as.data.frame( df.h.1 )
  df.2 <- as.data.frame( df.h.2 )
#  df.3 <- as.data.frame( df.h.3 )

  expect_that(all( df.1 == c(3,7,11) ), is_true())
  expect_that(all( df.2 == c(9, 12 ) ), is_true())
#  expect_that(all( df.3[,1] == c(2,4,6) ))
#  expect_that(all( df.3[,2] == c(3,5,7) ))

  
}

h2oTest.doTest('apply', applytest)
