setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.columndereference <- function() {
  h2oTest.logInfo('test column dereference')

  hdf <- h2o.importFile(h2oTest.locate('smalldata/jira/pub-180.csv'))
  otherhdf <- h2o.importFile(h2oTest.locate('smalldata/jira/v-11.csv'))

  column <- 'colgroup2'

  # get a single column out
  expect_that( dim(hdf[['colgroup2']]), equals(c(12,1)) )
  expect_that( dim(hdf[[column]]), equals(c(12,1)) )

  # NULL if column name doesn't exist
  expect_that( hdf[['col2group2']], equals(NULL))

  # we can overwrite a column
  hdf[['colgroup2']] <- hdf[['col2']]
  ldf <- as.data.frame( hdf[[ column ]] )[,1]
  expect_that(ldf, equals(c(2,4,6,11,3,4,6,11,2,4,6,11)) )

  
}

h2oTest.doTest("test column dereference and assignment", test.columndereference)
