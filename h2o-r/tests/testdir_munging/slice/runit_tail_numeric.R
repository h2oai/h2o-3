##
# Test tail of a data frame, check right result with R
# Author: Spencer
# Written under H2O git-hash: f983f28b1d07987105f122415768eb95f8c22f6a
##
# AutoGen meta:
# SEED: 
# task:
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.tail.numeric <- function(conn) {
  Log.info("Importing USArrests.csv data...")
  arrests.hex = h2o.uploadFile(conn, normalizePath(locate("smalldata/pca_test/USArrests.csv")), "arrests.hex")  

  Log.info("Check that tail works...")
  tail(arrests.hex)

  tail_ <- tail(arrests.hex)
  

  Log.info("Check that we get a data frame back from the tail(hex)")
  expect_that(tail_, is_a("data.frame"))
  
  tail_2 <- tail(USArrests)
  len <- nrow(USArrests)
  len_tail <- nrow(tail_2)
  rownames(tail_2) <- (len-len_tail+1):len #remove state names from USArrests

  Log.info("Check that the tail of the dataset is the same as what R produces: ")
  Log.info("tail(USArrests)")
  Log.info(tail_2)
  Log.info("tail(arrests.hex)")
  Log.info(tail_)
  df <- tail_ - tail_2
  expect_that(sum(df), is_less_than(1e-10))
  if( nrow(arrests.hex) <= view_max) {
    Log.info("Try doing tail ../ n > nrows(data). Should do same thing as R (returns all rows)")
    Log.info(paste("Data has ", paste(nrow(arrests.hex), " rows",sep=""),sep=""))
    tail_max <- tail(arrests.hex,nrow(arrests.hex) + 1)
  }
  testEnd()
}

doTest("Tail Tests", test.tail.numeric)
