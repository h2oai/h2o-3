##
# Parse airlines_all
##

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# setupRandomSeed(1994831827)

test <- function() {
	fr <- h2o.importFile(locate("smalldata/airlines/allyears2k_headers.zip"), "hex")
  cols <- h2o.filterNACols(fr, frac=.02)
  #[1]  1  2  3  4  6  8  9 10 11 13 17 18 19 22 24 30 31

  expect_that(cols, equals(c(1,2,3,4,6,8,9,10,11,13,17,18,19,22,24,30,31)))
  
}

doTest("Try filtering 0.02 or more NAs", test)

