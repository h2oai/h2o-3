##
# Test out the dim() functionality
# If data frame, get back a vector of two numbers: [1] nrows ncols
# If NAs in the frame, they still count.
# If not a frame, expect NULL
##


setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test.dim <- function() {
  Log.info("Uploading logreg/princeton/cuse.dat")
  hex <- h2o.importFile(locate("smalldata/logreg/prostate.csv"), "pros.hex")
  
  Rdat <- read.csv(locate("smalldata/logreg/prostate.csv"))
  
  Log.info("The dimension of the data when read into R is: ")
  print(dim(Rdat))
  
  Log.info("The dimension of the data when asking h2o: ")
  print(dim(hex))
  
  expect_that(dim(Rdat), equals(dim(hex)))

  Log.info("Slice out a column and data frame it, try dim on it...")
  
  h2oCol <- hex[,5]
  RCol <- Rdat[,5]
 
  Log.info("dim(h2oCol)") 
  print(dim(h2oCol))

  RCol <- data.frame(RCol)
  
  Log.info("dim(RCol)")
  print(dim(RCol))

  expect_that(dim(RCol), equals(dim(h2oCol)))
 
  Log.info("OK, now try an operator, e.g. '&', and then check dimensions agao...")
  
  RColAmpFive <- RCol & 5
  h2oColAmpFive <- h2oCol & 5
  
  expect_that(dim(RColAmpFive), equals(dim(h2oColAmpFive)))
  
  Log.info("Final check: data frame h2oCol then perform operation, should be the same as others...")
  Log.info("data.frame(as.data.frame(h2oCol))")
  print(data.frame(as.data.frame(h2oCol)))
  h2oTOR <- data.frame(as.data.frame(h2oCol) & 5)
  Log.info("as.data.frame(h2oCol) & 5")
  expect_that(dim(h2oTOR), equals(dim(h2oColAmpFive)))
  testEnd()
}



doTest("Test out the dim() functionality", test.dim)
