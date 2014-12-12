######################################################################
# Test for PUB-479
# Relational operators should work with vectors of different length.
# Replicate shorter vector, truncating when necessary, and do element-
# wise comparison.
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

test.relvec <- function(conn) {
  Log.info('Uploading iris_wheader.csv to H2O...')
  irisH2O <- h2o.importFile(conn, normalizePath(locate('smalldata/iris/iris_wheader.csv')))
  
  Log.info('Print head of dataset')
  Log.info(head(irisH2O))
  
  Log.info("Slice a subset of columns")
  petallenH2O <- irisH2O[,3]
  petalwidH2O <- irisH2O[,4]
  
  Log.info("Slice again and apply scale over columns")
  compH2O1 <- petallenH2O == c(1.4, 1.3)
  compH2O2 <- petallenH2O == c(1.3, 1.4)
  compH2O3 <- petalwidH2O == c(0.4, 0.2, 0.2)
  
  irisR <- read.csv(locate("smalldata/iris/iris_wheader.csv"), header = TRUE)
  petalR <- irisR[,3:4]
  compR1 <- petalR[,1] == c(1.4, 1.3)
  compR2 <- petalR[,1] == c(1.3, 1.4)
  compR3 <- petalR[,2] == c(0.4, 0.2, 0.2)
  
  Log.info("Comparing results to R")
  compH2O1.df <- as.logical(as.data.frame(compH2O1))
  compH2O2.df <- as.logical(as.data.frame(compH2O2))
  compH2O3.df <- as.logical(as.data.frame(compH2O3))
  
  expect_that(compH2O1.df, equals(as.data.frame(compR1)))
  expect_that(compH2O2.df, equals(as.data.frame(compR2)))
  expect_that(compH2O3.df, equals(as.data.frame(compR3)))
  
  testEnd()
}

doTest("PUB-479 Test: Relational operators over columns of different length", test.relvec)